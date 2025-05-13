/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.osgi.technology.webservices.integration.tests;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.service.webservice.runtime.WebserviceServiceRuntime;
import org.osgi.service.webservice.runtime.dto.EndpointDTO;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

/**
 * Test integration with http-whiteboard publisher
 */
@ExtendWith(ServiceExtension.class)
@ExtendWith(BundleContextExtension.class)
public class RegisterWithServletWhiteboardTestCase extends TestBase {

    @InjectService(timeout = 10000)
    WebserviceServiceRuntime runtime;

    @Test
    public void testEchoService(@InjectBundleContext BundleContext bundleContext) throws Exception {
        EndpointDTO dto = registerEchoEndpoint(bundleContext, UUID.randomUUID().toString(), "/echo");
        System.out.println(dto.address);
        assertEndpointEcho(dto);
    }

    @Override
    protected WebserviceServiceRuntime getRuntime() {
        return runtime;
    }
}
