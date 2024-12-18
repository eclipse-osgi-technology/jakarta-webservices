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
import org.osgi.service.webservice.runtime.dto.EndpointDTO;
import org.osgi.service.webservice.runtime.dto.FailedEndpointDTO;
import org.osgi.service.webservice.runtime.dto.HandlerDTO;

import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.handler.Handler;

class EndpointInfo {

    private final ServiceReference<?> reference;
    private final BundleContext bundleContext;
    private Exception lookupError;
    private Exception createError;
    private RuntimeException handlerError;
    private Exception publishError;
    private Object service;
    private Endpoint endpoint;
    private List<HandlerInfo> handlerList;
    private BundleEndpointContext endpointContext;
    private PublishedEndpoint publishedEndpoint;

    EndpointInfo(ServiceReference<?> reference, BundleContext bundleContext) {
        this.reference = reference;
        this.bundleContext = bundleContext;
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
            try {
                endpoint = Endpoint.create(Objects.requireNonNull(fetchImplementor()));
                endpoint.setProperties(getServiceProperties());
            } catch (RuntimeException e) {
                createError = e;
                return null;
            }
            handlerList = handlerMap.values().stream().filter(handlerInfo -> handlerInfo.matches(reference))
                    .sorted(HandlerInfo.SORT_BY_PRIORITY).toList();
            @SuppressWarnings("rawtypes") // required by API...
            List<Handler> chain = handlerList.stream().map(info -> info.fetchHandler()).filter(Objects::nonNull)
                    .map(Handler.class::cast).toList();
            if (!chain.isEmpty()) {
                try {
                    endpoint.getBinding().setHandlerChain(chain);
                } catch (RuntimeException e) {
                    handlerError = e;
                    return null;
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
