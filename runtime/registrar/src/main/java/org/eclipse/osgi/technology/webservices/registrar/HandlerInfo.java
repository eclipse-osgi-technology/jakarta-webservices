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
import java.util.Objects;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.webservice.runtime.dto.FailedHandlerDTO;
import org.osgi.service.webservice.runtime.dto.HandlerDTO;
import org.osgi.service.webservice.whiteboard.WebserviceWhiteboardConstants;

import jakarta.xml.ws.handler.Handler;
import jakarta.xml.ws.handler.MessageContext;

final class HandlerInfo {

    static final Comparator<HandlerInfo> SORT_BY_PRIORITY = Comparator.comparingInt(HandlerInfo::getPriority)
            .reversed();
    private final ServiceReference<Handler<? extends MessageContext>> reference;
    private final int priority;
    private BundleContext bundleContext;
    private Handler<? extends MessageContext> service;
    private Exception lookupError;
    private Exception filterError;

    HandlerInfo(ServiceReference<Handler<? extends MessageContext>> reference, BundleContext bundleContext) {
        this.reference = reference;
        this.bundleContext = bundleContext;
        Integer p = (Integer) reference.getProperty(Constants.SERVICE_RANKING);
        if (p == null) {
            this.priority = 0;
        } else {
            this.priority = p.intValue();
        }
    }

    synchronized boolean matches(ServiceReference<?> endpointImplementor) {
        Object epSelect = reference.getProperty(WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_FILTER);
        if (epSelect == null) {
            // if there is no selector this handler matches everywhere
            return true;
        }
        if (epSelect instanceof String fs) {
            if (fs.isBlank()) {
                // empty means all matching
                return true;
            }
            try {
                return FrameworkUtil.createFilter(fs).match(endpointImplementor);
            } catch (InvalidSyntaxException | RuntimeException e) {
                filterError = e;
            }
        } else {
            filterError = new IllegalArgumentException(
                    WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_FILTER + " must be of type string");
        }
        return false;
    }

    int getPriority() {
        return priority;
    }

    synchronized void dispose() {
        try {
            if (service != null) {
                bundleContext.ungetService(reference);
            }
        } catch (RuntimeException e) {
            // nothing we can do here...
        } finally {
            service = null;
            lookupError = null;
            filterError = null;
        }
    }

    synchronized Handler<? extends MessageContext> fetchHandler() {
        if (service == null) {
            try {
                service = Objects.requireNonNull(bundleContext.getService(reference));
            } catch (RuntimeException e) {
                lookupError = e;
            }
        }
        return service;
    }

    synchronized HandlerDTO getDto() {
        if (service == null || lookupError != null || filterError != null) {
            // if the service is null this means the handler is never fetched and therefore
            // unbound, or the fetching has failed or this instance was disposed!
            return null;
        }
        HandlerDTO dto = new HandlerDTO();
        dto.serviceReference = reference.adapt(ServiceReferenceDTO.class);
        return dto;
    }

    synchronized FailedHandlerDTO getFailedDto() {
        if (filterError != null) {
            FailedHandlerDTO dto = new FailedHandlerDTO();
            dto.failureCode = FailedHandlerDTO.FAILURE_REASON_INVALID_FILTER;
            dto.failureMessage = filterError.getMessage();
            dto.serviceReference = reference.adapt(ServiceReferenceDTO.class);
            return dto;
        }
        if (lookupError != null) {
            FailedHandlerDTO dto = new FailedHandlerDTO();
            dto.failureCode = FailedHandlerDTO.FAILURE_REASON_SERVICE_NOT_GETTABLE;
            dto.failureMessage = lookupError.getMessage();
            dto.serviceReference = reference.adapt(ServiceReferenceDTO.class);
            return dto;
        }
        if (service == null) {
            FailedHandlerDTO dto = new FailedHandlerDTO();
            dto.failureCode = FailedHandlerDTO.FAILURE_REASON_NO_MATCHING_ENDPOINT;
            dto.serviceReference = reference.adapt(ServiceReferenceDTO.class);
            return dto;
        }
        return null;
    }

}
