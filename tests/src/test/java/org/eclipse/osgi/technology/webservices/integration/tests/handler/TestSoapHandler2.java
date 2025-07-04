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
package org.eclipse.osgi.technology.webservices.integration.tests.handler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;

import jakarta.xml.ws.handler.LogicalMessageContext;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;

/**
 * A handler that allows inspection of the messages it handled
 */
public class TestSoapHandler2 implements SOAPHandler<SOAPMessageContext> {

    /**
     * The number of messages handled by this handler
     */
    public AtomicInteger handledMessages = new AtomicInteger();

    @Override
    public boolean handleMessage(SOAPMessageContext context) {
        boolean outbound = (boolean) context.get(LogicalMessageContext.MESSAGE_OUTBOUND_PROPERTY);
        int msg = handledMessages.incrementAndGet();
        System.out.println("[" + (outbound ? "outbound" : "inbound") + "] TestSoapHandler2.handleMessage no. " + msg);
        return true;
    }

    @Override
    public boolean handleFault(SOAPMessageContext context) {
        System.out.println("TestSoapHandler2.handleFault()");
        return true;
    }

    @Override
    public void close(MessageContext context) {
        System.out.println("TestSoapHandler2.close()");
    }

    @Override
    public Set<QName> getHeaders() {
        return Set.of();
    }

}
