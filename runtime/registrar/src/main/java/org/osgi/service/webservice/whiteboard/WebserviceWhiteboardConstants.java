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
package org.osgi.service.webservice.whiteboard;

/**
 * Defines standard constants for the Jakarta Web Services Whiteboard services.
 *
 * @author $Id$
 */
public class WebserviceWhiteboardConstants {

    private WebserviceWhiteboardConstants() {
        // non-instantiable
    }

    /**
     * Base namespace for the Webservice Whiteboard specification
     */
    public static final String WEBSERVICE = "osgi.service.webservice";
    /**
     * Base prefix used in component property types
     */
    public static final String WEBSERVICE_PREFIX = WEBSERVICE + ".";
    /**
     * Prefix used for properties of an endpoint implementor
     */
    public static final String WEBSERVICE_ENDPOINT_PREFIX = WEBSERVICE_PREFIX + "endpoint.";

    /**
     * Prefix used for properties of an message handler implementor
     */
    public static final String WEBSERVICE_HANDLER_PREFIX = WEBSERVICE_PREFIX + "handler.";

    /**
     * prefix used for properties related to the http transport
     */
    public static final String WEBSERVICE_HTTP_ENDPOINT_PREFIX = WEBSERVICE_ENDPOINT_PREFIX + "http.";
    /**
     * property used to mark a service as an endpoint implementor
     */
    public static final String WEBSERVICE_ENDPOINT_IMPLEMENTOR = WEBSERVICE_ENDPOINT_PREFIX + "implementor";

    /**
     * property that defines the address to use where endpoints should be published
     */
    public static final String WEBSERVICE_ENDPOINT_ADDRESS = WEBSERVICE_ENDPOINT_PREFIX + "address";

    /**
     * property to define a filter that allows handlers to select a given endpoint
     * as their target
     */
    public static final String WEBSERVICE_HANDLER_FILTER = WEBSERVICE_HANDLER_PREFIX + "filter";

    /**
     * property to define that a handler registered as a service must be considered
     * by the whiteboard
     */
    public static final String WEBSERVICE_HANDLER_EXTENSION = WEBSERVICE_HANDLER_PREFIX + "extension";
    /**
     * Specification version
     */
    public static final String WEBSERVICE_SPECIFICATION_VERSION = "1.0";

}
