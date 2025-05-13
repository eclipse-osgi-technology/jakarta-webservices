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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.assertj.core.api.Assertions;
import org.eclipse.osgi.technology.webservices.integration.tests.binding.WSEchoService;
import org.eclipse.osgi.technology.webservices.integration.tests.implementor.WSEcho;
import org.osgi.framework.BundleContext;
import org.osgi.service.webservice.runtime.WebserviceServiceRuntime;
import org.osgi.service.webservice.runtime.dto.EndpointDTO;
import org.osgi.service.webservice.runtime.dto.RuntimeDTO;
import org.osgi.service.webservice.whiteboard.WebserviceWhiteboardConstants;

public abstract class TestBase {

    protected static final String KEY_UUUID = "UUUID";

    protected Hashtable<String, Object> getImplementorProperties(String publishAddress) {
        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(WebserviceWhiteboardConstants.WEBSERVICE_ENDPOINT_IMPLEMENTOR, true);
        if (publishAddress.startsWith("http://")) {
            properties.put(WebserviceWhiteboardConstants.WEBSERVICE_ENDPOINT_ADDRESS, publishAddress);
        } else {
            properties.put(WebserviceWhiteboardConstants.WEBSERVICE_HTTP_ENDPOINT_PREFIX + "contextPath",
                    publishAddress);
        }
        return properties;
    }

    protected EndpointDTO registerEchoEndpoint(BundleContext bundleContext, String id, String publishAddress) {
        Hashtable<String, Object> properties = getImplementorProperties(publishAddress);
        properties.put(KEY_UUUID, id);
        bundleContext.registerService(WSEcho.class, new WSEcho(), properties);
        EndpointDTO endpoint = waitForDTO(10, SECONDS, dto -> {
            assertThat(dto.endpoints).as("Endpoints DTO").isNotNull();
            for (EndpointDTO ep : dto.endpoints) {
                assertThat(ep.implementor).as("Endpoint Implementor DTO").isNotNull();
                if (ep.implementor.bundle == bundleContext.getBundle().getBundleId()) {
                    System.out.println("Waiting for " + id + " eq " + ep.implementor.properties.get(KEY_UUUID));
                    if (id.equals(ep.implementor.properties.get(KEY_UUUID))) {
                        assertThat(ep.address).as("Publish Address").isNotNull();
                        return ep;
                    }
                }
            }
            return null;
        });
        return endpoint;
    }

    protected <T> T waitForDTO(long time, TimeUnit unit, Function<RuntimeDTO, T> tester) {
        return waitForDTO(time, unit, tester, "Timeout waiting for valid DTO");
    }

    protected <T> T waitForDTO(long time, TimeUnit unit, Function<RuntimeDTO, T> tester, String msg) {
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        WebserviceServiceRuntime runtime = getRuntime();
        assertNotNull(runtime, "WebserviceServiceRuntime can't be null");
        while (System.currentTimeMillis() < deadline) {
            RuntimeDTO runtimeDTO = runtime.getRuntimeDTO();
            assertThat(runtimeDTO).as("RuntimeDTO").isNotNull();
            T result = tester.apply(runtimeDTO);
            if (result != null) {
                return result;
            }
            Thread.yield();
        }
        Assertions.fail(msg + " // current DTO: " + getRuntime().getRuntimeDTO());
        return null;
    }

    protected static void assertEndpointEcho(EndpointDTO endpoint) throws MalformedURLException {
        WSEchoService service = new WSEchoService(new URL(endpoint.address + "?wsdl"));
        String textIn = "Hello World";
        String echo = service.getEchoPort().echo(textIn);
        assertThat(echo).as("Returned Text").isEqualTo(textIn);
    }

    protected abstract WebserviceServiceRuntime getRuntime();
}
