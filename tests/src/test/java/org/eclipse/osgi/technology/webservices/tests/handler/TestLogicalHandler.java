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
package org.eclipse.osgi.technology.webservices.tests.handler;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.xml.ws.handler.LogicalHandler;
import jakarta.xml.ws.handler.LogicalMessageContext;
import jakarta.xml.ws.handler.MessageContext;

/**
 * A logical handler that allows inspection of the handled messages
 */
public class TestLogicalHandler implements LogicalHandler<LogicalMessageContext> {

	/**
	 * A counter of number of messages handled here
	 */
	public AtomicInteger handledMessages = new AtomicInteger();

	@Override
	public boolean handleMessage(LogicalMessageContext context) {
		int msg = handledMessages.incrementAndGet();
		System.out.println("TestLogicalHandler.handleMessage no. " + msg);
		return true;
	}

	@Override
	public boolean handleFault(LogicalMessageContext context) {
		System.out.println("TestLogicalHandler.handleFault()");
		return true;
	}

	@Override
	public void close(MessageContext context) {
		System.out.println("TestLogicalHandler.close()");
	}

}
