/*******************************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.technology.webservices.registrar;

import java.lang.annotation.Annotation;
import java.util.Dictionary;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.osgi.technology.webservices.spi.EndpointPublisher;
import org.osgi.annotation.bundle.Capability;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.namespace.implementation.ImplementationNamespace;
import org.osgi.namespace.service.ServiceNamespace;
import org.osgi.service.component.AnyService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.service.webservice.runtime.WebserviceServiceRuntime;
import org.osgi.service.webservice.runtime.dto.EndpointDTO;
import org.osgi.service.webservice.runtime.dto.FailedEndpointDTO;
import org.osgi.service.webservice.runtime.dto.FailedHandlerDTO;
import org.osgi.service.webservice.runtime.dto.HandlerDTO;
import org.osgi.service.webservice.runtime.dto.RuntimeDTO;
import org.osgi.service.webservice.whiteboard.WebserviceWhiteboardConstants;

import jakarta.xml.ws.handler.Handler;
import jakarta.xml.ws.handler.MessageContext;

/**
 * Endpoint registrar implementation
 */
@Component(immediate = true, service = {})
@Capability(namespace = ImplementationNamespace.IMPLEMENTATION_NAMESPACE, //
        name = WebserviceWhiteboardConstants.WEBSERVICE, //
        version = WebserviceWhiteboardConstants.WEBSERVICE_SPECIFICATION_VERSION)
@Capability(namespace = ServiceNamespace.SERVICE_NAMESPACE, attribute = {ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE
        + ":List<String>=\"org.osgi.service.webservice.runtime.WebserviceServiceRuntime\";uses:=\"org.osgi.service.webservice.runtime\"" })
public class EndpointRegistrar implements WebserviceServiceRuntime {

    private Logger logger;

    private Map<Bundle, BundleEndpointContext> contextMap = new WeakHashMap<>();
    private Map<ServiceReference<?>, HandlerInfo> handlerMap = new ConcurrentHashMap<>();
    private Map<ServiceReference<?>, EndpointRegistration> endpointRegistrations = new ConcurrentHashMap<>();
    private Map<EndpointPublisher, ServiceRanking> endpointPublisherMap = new ConcurrentHashMap<>();
    private ComponentContext context;
    private final AtomicLong changeCount = new AtomicLong();

    private ServiceRegistration<WebserviceServiceRuntime> registerService;

    /**
     * contructor
     *
     * @param logger  logger
     * @param context context
     */
    @Activate
    public EndpointRegistrar(@Reference(service = LoggerFactory.class) Logger logger, ComponentContext context) {
        this.logger = logger;
        this.context = context;
        // WORKAROUND for https://github.com/osgi/osgi/issues/809
        registerService = context.getBundleContext().registerService(WebserviceServiceRuntime.class, this,
                getProperties());

    }

    private Dictionary<String, Long> getProperties() {
        return FrameworkUtil.asDictionary(Map.of(Constants.SERVICE_CHANGECOUNT, changeCount.getAndIncrement()));
    }

    /**
     * adds an endpoint
     *
     * @param publisher  publisher
     * @param properties properties
     */
    @Reference(cardinality = ReferenceCardinality.AT_LEAST_ONE, policy = ReferencePolicy.DYNAMIC)
    public void addEndpointPublisher(EndpointPublisher publisher, Map<String, ?> properties) {
        ServiceRanking ranking = new ServiceRanking() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return ServiceRanking.class;
            }

            @Override
            public int value() {
                Object object = properties.get(Constants.SERVICE_RANKING);
                if (object instanceof Integer) {
                    return ((Integer) object).intValue();
                }
                return 0;
            }
        };
        logger.debug("BINDING publisher={} with ranking={}", publisher, ranking);
        endpointPublisherMap.put(publisher, ranking);
        updateAll();
        registerService.setProperties(getProperties());
    }

    /**
     * removes endpoint
     *
     * @param publisher publisher
     */
    public void removeEndpointPublisher(EndpointPublisher publisher) {
        ServiceRanking ranking = endpointPublisherMap.remove(publisher);
        logger.debug("UNBINDING publisher={} with ranking={}", publisher, ranking);
        updateAll();
        registerService.setProperties(getProperties());
    }

    /**
     * bind endpoint
     *
     * @param endpointImplementorReference ref
     */
    @Reference(service = AnyService.class, target = "(" + WebserviceWhiteboardConstants.WEBSERVICE_ENDPOINT_IMPLEMENTOR
            + "=true)", cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    public void bindEndpointImplementor(ServiceReference<?> endpointImplementorReference) {
        logger.debug("BINDING implementor={}", endpointImplementorReference);
        EndpointRegistration registration = new EndpointRegistration(endpointImplementorReference);
        EndpointRegistration replaced = endpointRegistrations.put(endpointImplementorReference, registration);
        if (replaced != null) {
            replaced.dispose();
        }
        registration.refresh();
        registerService.setProperties(getProperties());
    }

    /**
     * unbind endpoint
     *
     * @param endpointImplementorReference ref
     */
    public void unbindEndpointImplementor(ServiceReference<?> endpointImplementorReference) {
        logger.debug("UNBINDING implementor={}", endpointImplementorReference);
        EndpointRegistration registration = endpointRegistrations.remove(endpointImplementorReference);
        if (registration != null) {
            registration.dispose();
            registerService.setProperties(getProperties());
        }
    }

    /**
     * add handler
     *
     * @param handler handler
     */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void addHandler(ServiceReference<Handler<? extends MessageContext>> handler) {
        logger.debug("UPDATE handler={}", handler);
        HandlerInfo info = handlerMap.put(handler, new HandlerInfo(handler, context.getBundleContext()));
        if (info != null) {
            info.dispose();
        }
        updateAll();
        registerService.setProperties(getProperties());
    }

    /**
     * update handler
     *
     * @param handler handler
     */
    public void updateHandler(ServiceReference<Handler<? extends MessageContext>> handler) {
        logger.debug("UPDATE handler={}", handler);
        HandlerInfo info = handlerMap.put(handler, new HandlerInfo(handler, context.getBundleContext()));
        if (info != null) {
            info.dispose();
        }
        updateAll();
        registerService.setProperties(getProperties());
    }

    /**
     * remove handler
     *
     * @param handler handler
     */
    public void removeHandler(ServiceReference<Handler<? extends MessageContext>> handler) {
        logger.debug("REMOVE handler={}", handler);
        HandlerInfo info = handlerMap.remove(handler);
        if (info != null) {
            info.dispose();
            updateAll();
            registerService.setProperties(getProperties());
        }
    }

    private void updateAll() {
        logger.debug("Update all handlers...");
        endpointRegistrations.values().stream().forEach(EndpointRegistration::refresh);
    }

    private final class EndpointRegistration {

        private EndpointInfo endpointInfo;

        public EndpointRegistration(ServiceReference<?> implementorReference) {
            endpointInfo = new EndpointInfo(implementorReference, context.getBundleContext());
        }

        synchronized void refresh() {
            if (endpointInfo == null) {
                return;
            }
            endpointInfo = new EndpointInfo(endpointInfo.dispose(), context.getBundleContext());
            endpointInfo.publishEndpoint(handlerMap, contextMap, endpointPublisherMap);
        }

        synchronized void dispose() {
            endpointInfo.dispose();
            endpointInfo = null;
        }

    }

    @Override
    public RuntimeDTO getRuntimeDTO() {
        RuntimeDTO runtimeDTO = new RuntimeDTO();
        runtimeDTO.endpoints = endpointRegistrations.values().stream().map(reg -> reg.endpointInfo)
                .filter(Objects::nonNull).map(epi -> epi.getEndpointDTO()).filter(Objects::nonNull)
                .toArray(EndpointDTO[]::new);
        runtimeDTO.failedEndpoints = endpointRegistrations.values().stream().map(reg -> reg.endpointInfo)
                .filter(Objects::nonNull).map(epi -> epi.getFailedEndpointDTO()).filter(Objects::nonNull)
                .toArray(FailedEndpointDTO[]::new);
        runtimeDTO.handlers = handlerMap.values().stream().map(hi -> hi.getDto()).filter(Objects::nonNull)
                .toArray(HandlerDTO[]::new);
        runtimeDTO.failedHandlers = handlerMap.values().stream().map(hi -> hi.getFailedDto()).filter(Objects::nonNull)
                .toArray(FailedHandlerDTO[]::new);
        runtimeDTO.serviceReference = registerService.getReference().adapt(ServiceReferenceDTO.class);
        return runtimeDTO;
    }

}
