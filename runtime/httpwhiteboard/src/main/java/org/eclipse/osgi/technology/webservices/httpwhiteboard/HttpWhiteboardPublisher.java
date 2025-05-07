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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.osgi.technology.webservices.spi.EndpointPublisher;
import org.eclipse.osgi.technology.webservices.spi.PublishedEndpoint;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.whiteboard.annotations.RequireHttpWhiteboard;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.service.webservice.whiteboard.WebserviceWhiteboardConstants;
import org.osgi.service.webservice.whiteboard.annotations.RequireWebserviceWhiteboard;

import jakarta.xml.ws.Endpoint;

/**
 * Publishes Endpoints with the HttpWhiteboard service
 *
 */
@RequireHttpWhiteboard
@RequireWebserviceWhiteboard
@Component(immediate = true, name = "org.eclipse.osgi.technology.webservices.httpwhiteboard", service = EndpointPublisher.class)
public class HttpWhiteboardPublisher implements EndpointPublisher {

	private BundleContext bundleContext;
	private Logger logger;
	private Map<Endpoint, WhiteboardHttpContext> registrationMap = new ConcurrentHashMap<>();

	/**
	 * Constructor
	 * 
	 * @param bundleContext the context
	 * @param logger        the logger
	 */
	@Activate
	public HttpWhiteboardPublisher(BundleContext bundleContext,
			@Reference(service = LoggerFactory.class) Logger logger) {
		this.bundleContext = bundleContext;
		this.logger = logger;
	}

	@Override
	public PublishedEndpoint publishEndpoint(Endpoint endpoint) {
		Map<String, Object> properties = endpoint.getProperties();
		Object prefix = properties.get(WebserviceWhiteboardConstants.WEBSERVICE_HTTP_ENDPOINT_PREFIX);
		if (prefix instanceof String contextPath) {
			logger.info("Registering {} with http whiteboard at context path {}", endpoint, contextPath);
			WhiteboardHttpContext httpContext = new WhiteboardHttpContext(contextPath, endpoint.getProperties());
			registrationMap.put(endpoint, httpContext);
			endpoint.publish(httpContext);
			httpContext.register(bundleContext);
			return httpContext;
		}
		return null;
	}

}
