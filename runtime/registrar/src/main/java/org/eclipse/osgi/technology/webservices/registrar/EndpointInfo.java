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

import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.eclipse.osgi.technology.webservices.spi.EndpointPublisher;
import org.eclipse.osgi.technology.webservices.spi.PublishedEndpoint;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.log.Logger;
import org.osgi.service.webservice.runtime.dto.EndpointDTO;
import org.osgi.service.webservice.runtime.dto.FailedEndpointDTO;
import org.osgi.service.webservice.runtime.dto.HandlerDTO;

import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.handler.Handler;

class EndpointInfo {

    private final ServiceReference<?> reference;
    private final BundleContext bundleContext;
    private final Logger logger;
    private Exception lookupError;
    private Exception createError;
    private RuntimeException handlerError;
    private Exception publishError;
    private Object service;
    private Endpoint endpoint;
    private List<HandlerInfo> handlerList;
    private BundleEndpointContext endpointContext;
    private PublishedEndpoint publishedEndpoint;

    EndpointInfo(ServiceReference<?> reference, BundleContext bundleContext, Logger logger) {
        this.reference = reference;
        this.bundleContext = bundleContext;
        this.logger = logger;
    }

    synchronized ServiceReference<?> dispose() {
        try {
            if (publishedEndpoint != null) {
                publishedEndpoint.unpublish();
            }
        } catch (RuntimeException e) {
            // nothing we can do here...
        }
        try {
            if (endpoint != null) {
                if (endpointContext != null) {
                    endpointContext.removeEndpoint(endpoint);
                }
                endpoint.stop();
            }
        } catch (RuntimeException e) {
            // nothing we can do here...
        }
        try {
            if (service != null) {
                bundleContext.ungetService(reference);
            }
        } catch (RuntimeException e) {
            // nothing we can do here...
        }
        publishedEndpoint = null;
        publishError = null;
        endpoint = null;
        createError = null;
        service = null;
        lookupError = null;
        handlerList = null;
        handlerError = null;
        endpointContext = null;
        return reference;
    }

    synchronized Object fetchImplementor() {
        if (service == null) {
            try {
                service = Objects.requireNonNull(bundleContext.getService(reference));
            } catch (RuntimeException e) {
                lookupError = e;
            }
        }
        return service;
    }

    synchronized Endpoint createEndpoint(Map<ServiceReference<?>, HandlerInfo> handlerMap,
            Map<Bundle, BundleEndpointContext> contextMap) {
        if (endpoint == null) {
            Object implementor;
            try {
                implementor = Objects.requireNonNull(fetchImplementor());
            } catch (RuntimeException e) {
                createError = e;
                return null;
            }
            handlerList = handlerMap.values().stream().filter(handlerInfo -> handlerInfo.matches(reference))
                    .sorted(HandlerInfo.SORT_BY_PRIORITY).toList();
            @SuppressWarnings("rawtypes") // required by API...
            List<Handler> chain = handlerList.stream().map(info -> info.fetchHandler()).filter(Objects::nonNull)
                    .map(Handler.class::cast).toList();

            Class<?> implClass = implementor.getClass();
            @SuppressWarnings("rawtypes")
            List<Handler> mergedChain = chain;
            boolean hasStaticChain = false;
            if (!chain.isEmpty() && StaticHandlerChain.isPresent(implClass)) {
                // We parse and merge the static @HandlerChain ourselves (independent of the
                // JAX-WS implementation in use) so the merged chain is available regardless
                // of whether the Metro-specific workaround below is applicable. This is
                // needed because Metro/jaxws-rt silently discards a handler chain set before
                // publish() whenever the implementor also declares a static @HandlerChain
                // (see https://github.com/eclipse-ee4j/metro-jax-ws/issues/812).
                try {
                    List<Handler> staticChain = StaticHandlerChain.parse(implClass);
                    if (!staticChain.isEmpty()) {
                        mergedChain = StaticHandlerChain.merge(staticChain, handlerList);
                        hasStaticChain = true;
                    }
                } catch (ReflectiveOperationException | java.io.IOException
                        | javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException
                        | RuntimeException e) {
                    logger.warn(
                            "Could not parse the static @HandlerChain declared on {}. The handler chain of this "
                                    + "endpoint may be incomplete.",
                            reference, e);
                }
            }

            if (hasStaticChain) {
                // Try to make the merged chain actually take effect at publish time via the
                // Metro-specific workaround; if that isn't possible we fall back to the
                // best-effort behaviour below.
                try {
                    endpoint = MetroHandlerChainWorkaround.tryCreate(implementor, mergedChain);
                    endpoint.setProperties(getServiceProperties());
                } catch (RuntimeException | LinkageError e) {
                    logger.warn(
                            "Could not apply the Metro static handler chain workaround for {} (see metro-jax-ws#812). "
                                    + "The handler chain of this endpoint may be incomplete because a static "
                                    + "@HandlerChain is declared alongside dynamic whiteboard handlers.",
                            reference, e);
                    endpoint = null;
                }
            }
            if (endpoint == null) {
                try {
                    endpoint = Endpoint.create(implementor);
                    endpoint.setProperties(getServiceProperties());
                } catch (RuntimeException e) {
                    createError = e;
                    return null;
                }
                if (!mergedChain.isEmpty()) {
                    try {
                        endpoint.getBinding().setHandlerChain(mergedChain);
                    } catch (RuntimeException e) {
                        handlerError = e;
                        return null;
                    }
                }
            }
            endpointContext = contextMap.computeIfAbsent(reference.getBundle(), BundleEndpointContext::new);
            endpointContext.addEndpoint(endpoint);
            try {
                endpoint.setEndpointContext(endpointContext);
            } catch (RuntimeException e) {
                // this is optional...
            }
        }
        return endpoint;
    }

    private Map<String, Object> getServiceProperties() {
        Hashtable<String, Object> hashtable = new Hashtable<>();
        Dictionary<String, Object> properties = reference.getProperties();
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (key.startsWith(".")) {
                // do not propagate secret properties
                continue;
            }
            hashtable.put(key, properties.get(key));
        }
        return hashtable;
    }

    synchronized PublishedEndpoint publishEndpoint(Map<ServiceReference<?>, HandlerInfo> handlerMap,
            Map<Bundle, BundleEndpointContext> contextMap,
            Map<EndpointPublisher, ServiceRanking> endpointPublisherMap) {
        Endpoint ep = createEndpoint(handlerMap, contextMap);
        if (ep == null) {
            return null;
        }
        try {
            publishedEndpoint = endpointPublisherMap.entrySet().stream()
                    .sorted(Comparator.comparing(Entry::getValue, Comparator.comparingInt(ServiceRanking::value)))
                    .map(Entry::getKey).map(publisher -> publisher.publishEndpoint(ep)).filter(Objects::nonNull)
                    .findFirst().orElseThrow(() -> new IllegalStateException("No applicable EndpointPublisher"));
        } catch (RuntimeException e) {
            publishError = e;
        }
        return publishedEndpoint;

    }

    synchronized EndpointDTO getEndpointDTO() {
        if (publishedEndpoint != null) {
            EndpointDTO dto = new EndpointDTO();
            dto.address = publishedEndpoint.getAddress();
            dto.handlers = handlerList.stream().map(hi -> hi.getDto()).filter(Objects::nonNull)
                    .toArray(HandlerDTO[]::new);
            dto.implementor = reference.adapt(ServiceReferenceDTO.class);
            return dto;
        }
        return null;
    }

    synchronized FailedEndpointDTO getFailedEndpointDTO() {
        if (lookupError != null) {
            return createFailed(lookupError, FailedEndpointDTO.FAILURE_REASON_SERVICE_NOT_GETTABLE);
        }
        if (createError != null) {
            return createFailed(createError, FailedEndpointDTO.FAILURE_REASON_CREATE_FAILED);
        }
        if (handlerError != null) {
            return createFailed(handlerError,
                    handlerError instanceof UnsupportedOperationException
                            ? FailedEndpointDTO.FAILURE_REASON_SET_HANDLER_NOT_SUPPORTED
                            : FailedEndpointDTO.FAILURE_REASON_SET_HANDLER_FAILED);
        }
        if (publishError != null) {
            return createFailed(publishError, FailedEndpointDTO.FAILURE_REASON_PUBLISH_FAILED);
        }
        return null;
    }

    private FailedEndpointDTO createFailed(Exception exception, int reason) {
        FailedEndpointDTO dto = new FailedEndpointDTO();
        dto.implementor = reference.adapt(ServiceReferenceDTO.class);
        dto.failureCode = reason;
        dto.failureMessage = exception.getMessage();
        return dto;
    }

}
