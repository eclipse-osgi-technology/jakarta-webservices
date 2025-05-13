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
package org.osgi.service.webservice.whiteboard.propertytypes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.osgi.service.component.annotations.ComponentPropertyType;
import org.osgi.service.webservice.whiteboard.WebserviceWhiteboardConstants;
import org.osgi.service.webservice.whiteboard.annotations.RequireWebserviceWhiteboard;

/**
 * Component property type to mark a Handler to be considered by the Jakarta XML
 * Webservices Whiteboard Runtime
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
        ElementType.TYPE
})
@RequireWebserviceWhiteboard
@ComponentPropertyType
public @interface XmlWsHandler {

    /**
     * prefix used for component properties
     */
    String PREFIX_ = WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_PREFIX;


    /**
     * Marks this component as to be considered by the whiteboard extender
     *
     * @return true
     */
    boolean extension() default true;

    /**
     * @return the filter to select an endpoint must be a valid OSGi filter, the
     *         empty string is treated as if the property is not defined and
     *         should match any endpoint
     */
    String filter() default "";

}
