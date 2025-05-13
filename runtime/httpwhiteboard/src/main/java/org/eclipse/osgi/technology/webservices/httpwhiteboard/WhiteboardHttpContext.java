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
package org.eclipse.osgi.technology.webservices.httpwhiteboard;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.osgi.technology.webservices.httpwhiteboard.wsri.EndpointHttpExchange;
import org.eclipse.osgi.technology.webservices.spi.PublishedEndpoint;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.runtime.HttpServiceRuntime;
import org.osgi.service.http.runtime.dto.RequestInfoDTO;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.xml.ws.spi.http.HttpContext;

/**
 * Http context to use with the whiteboard
 */
public class WhiteboardHttpContext extends HttpContext implements PublishedEndpoint {

    private String path;
    private Map<String, ?> attributes;
    private Set<String> names;
    private boolean closed;
    private ServiceRegistration<?> serviceRegistration;
    private AtomicReference<HttpServiceRuntime> httpRuntime;

    WhiteboardHttpContext(String path, Map<String, ?> attributes, AtomicReference<HttpServiceRuntime> httpRuntime) {
        this.path = path;
        this.attributes = attributes;
        this.httpRuntime = httpRuntime;
        names = Collections.unmodifiableSet(attributes.keySet());
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Set<String> getAttributeNames() {
        return names;
    }

    synchronized void register(BundleContext bundleContext) {
        if (closed) {
            return;
        }
        HashMap<String, Object> properties = new HashMap<>(attributes);
        if (!properties.containsKey(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME)) {
            properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME,
                    "JAX-WS Service for path " + getPath());
        }
        properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, getPath());
        serviceRegistration = bundleContext.registerService(Servlet.class, new JaxWsServlet(),
                FrameworkUtil.asDictionary(properties));
        System.out.println("Http Service registered with properties " + properties);
        System.out.println(
                "Service ID is " + serviceRegistration.getReference().getProperties().get(Constants.SERVICE_ID));
    }

    private class JaxWsServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            if (handler == null || closed) {
                resp.sendError(HttpURLConnection.HTTP_UNAVAILABLE);
                return;
            }
            handler.handle(new EndpointHttpExchange(req, resp, getServletContext(), WhiteboardHttpContext.this));
        }

    }

    @Override
    public synchronized void unpublish() {
        closed = true;
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }

    @Override
    public String getAddress() {
        System.out.println("WhiteboardHttpContext.getAddress()");
        HttpServiceRuntime runtime = httpRuntime.get();
        if (runtime != null) {
            RequestInfoDTO requestDto = runtime.calculateRequestInfoDTO(getPath());
            System.out.println(requestDto);
            System.out.println(runtime.getRuntimeDTO());
            Object endpoints = runtime.getRuntimeDTO().serviceDTO.properties.get("osgi.http.endpoint");
            try {
                if (endpoints instanceof String baseUrl) {
                    System.out.println("baseUrl = " + baseUrl);
                    String string = new URI(baseUrl).resolve(path).toString();
                    System.out.println("Full URL: " + string);
                    return string;
                } else if (endpoints instanceof String[] baseUrls && baseUrls.length > 0) {
                    System.out.println("baseUrl = " + baseUrls[0]);
                    String string = new URI(baseUrls[0]).resolve(path).toString();
                    System.out.println("Full URL: " + string);
                    return string;
                }
            } catch (URISyntaxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        // FIXME we need the full path here, how to get it from the whiteboard?!?
        return path;
    }

}
