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
package org.eclipse.osgi.technology.webservices.integration.tests;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Condition;
import org.eclipse.osgi.technology.webservices.integration.tests.handler.BadHandler;
import org.eclipse.osgi.technology.webservices.integration.tests.handler.InvalidHandler;
import org.eclipse.osgi.technology.webservices.integration.tests.handler.TestLogicalHandler;
import org.eclipse.osgi.technology.webservices.integration.tests.handler.TestSoapHandler;
import org.eclipse.osgi.technology.webservices.integration.tests.handler.TestSoapHandler2;
import org.eclipse.osgi.technology.webservices.integration.tests.implementor.BadImplementor;
import org.eclipse.osgi.technology.webservices.integration.tests.implementor.WSEcho;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.webservice.runtime.WebserviceServiceRuntime;
import org.osgi.service.webservice.runtime.dto.EndpointDTO;
import org.osgi.service.webservice.runtime.dto.FailedEndpointDTO;
import org.osgi.service.webservice.runtime.dto.FailedHandlerDTO;
import org.osgi.service.webservice.runtime.dto.HandlerDTO;
import org.osgi.service.webservice.whiteboard.WebserviceWhiteboardConstants;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

import jakarta.xml.ws.handler.Handler;

@ExtendWith(ServiceExtension.class)
@ExtendWith(BundleContextExtension.class)
public class JakartaWebserviceWhiteboardTestCase extends TestBase {

    private static final String    HANDLER_BAD                = "bad";
    private static final String    HANDLER_SOAP            = "soap";
    private static final String    HANDLER_LOGICAL            = "logical";
    private static final String    HANDLER_INVALID            = "invalid";
    private static final String    HANDLER_TYPE            = "type";
    private static final String    DEFAULT_PUBLISH_ADDRESS    = System.getProperty(
            "org.osgi.test.cases.jakartaws.defaultaddress",
            "http://localhost:8579");
    @InjectService(timeout = 10000)
    WebserviceServiceRuntime    runtime;

    @Test
    public void testEchoService(@InjectBundleContext
    BundleContext bundleContext) throws Exception {
        String id = UUID.randomUUID().toString();
        String filter = String.format("(%s=%s)", KEY_UUUID, id);
        TestLogicalHandler logicalHandler = registerLogicalHandler(
                bundleContext, filter);
        TestSoapHandler soapHandler = registerSoapHandler(bundleContext,
                filter);
        registerSoapHandler2(bundleContext, filter);
        String publishAddress = DEFAULT_PUBLISH_ADDRESS + "/wsecho";
        EndpointDTO endpoint = registerEchoEndpoint(bundleContext, id,
                publishAddress);
        assertThat(endpoint.address).as("Endpoint Address")
                .isEqualTo(publishAddress);
        assertThat(endpoint.handlers)
                .areAtLeastOne(new Condition<HandlerDTO>(dto -> {
                    return HANDLER_LOGICAL.equals(
                            dto.serviceReference.properties.get(HANDLER_TYPE));
                }, "LogicalHandler is bound"))
                .areAtLeastOne(new Condition<HandlerDTO>(dto -> {
                    return HANDLER_SOAP.equals(
                            dto.serviceReference.properties.get(HANDLER_TYPE));
                }, "SoapHandler is bound"));
        assertEndpointEcho(endpoint);
        assertThat(logicalHandler.handledMessages.get()).isEqualTo(2);
        assertThat(soapHandler.handledMessages.get()).isEqualTo(2);
    }

    @Test
    public void testFailedHandler(@InjectBundleContext BundleContext bundleContext) throws Exception {
        String publishAddress = DEFAULT_PUBLISH_ADDRESS + "/dontcare";
        String id = UUID.randomUUID().toString();
        String filter = String.format("(%s=%s)", KEY_UUUID, id);
        // This handler *does* match the webservice but returns a null service
        // on each call
        bundleContext.registerService(Handler.class, new BadHandler(), FrameworkUtil.asDictionary(
                Map.of(WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_FILTER, filter,
                        WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_EXTENSION, "true", HANDLER_TYPE,
                        HANDLER_BAD)));
        // this handler simply do not match at all
        registerLogicalHandler(bundleContext, "(a=b)");
        // this handler has an invalid filter
        registerSoapHandler(bundleContext, "XSYOJHF6&/8+#=");
        EndpointDTO endpoint = registerEchoEndpoint(bundleContext, id, publishAddress);
        assertThat(endpoint.handlers).areNot(new Condition<HandlerDTO>(dto -> {
            return HANDLER_BAD.equals(dto.serviceReference.properties.get(HANDLER_TYPE));
        }, "Bad Handler is bound"));
        assertFailedHandler(HANDLER_BAD, FailedHandlerDTO.FAILURE_REASON_SERVICE_NOT_GETTABLE);
        assertFailedHandler(HANDLER_LOGICAL, FailedHandlerDTO.FAILURE_REASON_NO_MATCHING_ENDPOINT);
        assertFailedHandler(HANDLER_SOAP, FailedHandlerDTO.FAILURE_REASON_INVALID_FILTER);
    }

    @Test
    public void testFailedEndpointFailedPublish(@InjectBundleContext
    BundleContext bundleContext) throws Exception {
        // The invalid implementor has no annotations
        String invalidPublishAddress = DEFAULT_PUBLISH_ADDRESS + "/invalid";
        bundleContext.registerService(Object.class, new Object(),
                getImplementorProperties(invalidPublishAddress));
        assertFailedEndpoint(invalidPublishAddress,
                FailedEndpointDTO.FAILURE_REASON_PUBLISH_FAILED);
    }

    @Test
    public void testFailedEndpointLockupFailed(@InjectBundleContext
    BundleContext bundleContext) throws Exception {
        // The bad implementor return null for its service!
        String badPublishAddress = DEFAULT_PUBLISH_ADDRESS + "/bad";
        bundleContext.registerService(String.class, new BadImplementor(),
                getImplementorProperties(badPublishAddress));
        assertFailedEndpoint(badPublishAddress,
                FailedEndpointDTO.FAILURE_REASON_SERVICE_NOT_GETTABLE);
    }

    @Test
    public void testFailedEndpointInvalidHandler(@InjectBundleContext BundleContext bundleContext) throws Exception {
        // Here we have a valid endpoint but invalid handler
        String wrongPublishAddress = DEFAULT_PUBLISH_ADDRESS + "/wrong";
        String id = UUID.randomUUID().toString();
        String filter = String.format("(%s=%s)", KEY_UUUID, id);
        bundleContext.registerService(Handler.class, new InvalidHandler(), FrameworkUtil.asDictionary(Map
                .of(WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_FILTER, filter,
                        WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_EXTENSION, "true", HANDLER_TYPE,
                        HANDLER_INVALID)));
        Hashtable<String, Object> properties = getImplementorProperties(wrongPublishAddress);
        properties.put(KEY_UUUID, id);
        bundleContext.registerService(WSEcho.class, new WSEcho(), properties);
        assertFailedEndpoint(wrongPublishAddress, FailedEndpointDTO.FAILURE_REASON_SET_HANDLER_FAILED);
    }

    private void assertFailedEndpoint(String publishAddress, int code) {
        FailedEndpointDTO failedEndpoint = waitForDTO(5, SECONDS, dto -> {
            assertThat(dto.failedEndpoints).as("Failed Endpoint DTO")
                    .isNotNull();
            for (FailedEndpointDTO failed : dto.failedEndpoints) {
                if (publishAddress.equals(failed.implementor.properties
                        .get(WebserviceWhiteboardConstants.WEBSERVICE_ENDPOINT_ADDRESS))) {
                    return failed;
                }
            }
            return null;
        }, "Endpoint " + publishAddress + " not marked as failed");
        assertThat(failedEndpoint.failureCode)
                .as("Failure Code of endpoint " + publishAddress + " (returned "
                        + failedEndpoint + ")")
                .isEqualTo(code);
    }

    private void assertFailedHandler(String type, int code) {
        FailedHandlerDTO failedBad = waitForDTO(5, SECONDS, dto -> {
            assertThat(dto.failedHandlers).as("Failed Handler DTO").isNotNull();
            for (FailedHandlerDTO failed : dto.failedHandlers) {
                if (type.equals(
                        failed.serviceReference.properties.get(HANDLER_TYPE))) {
                    return failed;
                }
            }
            return null;
        }, "Handler " + type + " not marked as failed");
        assertThat(failedBad.failureCode).as("Failure Code of handler " + type)
                .isEqualTo(code);
    }

    private TestSoapHandler registerSoapHandler(BundleContext bundleContext,
            String filter) {
        TestSoapHandler soapHandler = new TestSoapHandler();
        bundleContext.registerService(Handler.class, soapHandler,
                FrameworkUtil.asDictionary(
                        Map.of(WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_FILTER,
                                filter, WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_EXTENSION, true,
                                HANDLER_TYPE, HANDLER_SOAP)));
        return soapHandler;
    }

    private TestSoapHandler2 registerSoapHandler2(BundleContext bundleContext, String filter) {
        TestSoapHandler2 soapHandler = new TestSoapHandler2();
        bundleContext.registerService(Handler.class, soapHandler,
                FrameworkUtil.asDictionary(Map.of(WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_FILTER, filter,
                        WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_EXTENSION, "true", HANDLER_TYPE,
                        HANDLER_SOAP, Constants.SERVICE_RANKING, 100)));
        return soapHandler;
    }

    private TestLogicalHandler registerLogicalHandler(
            BundleContext bundleContext, String filter) {
        TestLogicalHandler logicalHandler = new TestLogicalHandler();
        bundleContext.registerService(Handler.class, logicalHandler,
                FrameworkUtil.asDictionary(
                        Map.of(WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_FILTER, filter,
                                WebserviceWhiteboardConstants.WEBSERVICE_HANDLER_EXTENSION, true, HANDLER_TYPE,
                                HANDLER_LOGICAL)));
        return logicalHandler;
    }

    @Override
    protected WebserviceServiceRuntime getRuntime() {
        return runtime;
    }

}
