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

import jakarta.xml.ws.handler.Handler;
import jakarta.xml.ws.handler.MessageContext;

/**
 * An invalid handler as it implements the Handler interface directly
 */
public class InvalidHandler implements Handler<MessageContext> {

	@Override
	public void close(MessageContext arg0) {

	}

	@Override
	public boolean handleFault(MessageContext arg0) {
		return true;
	}

	@Override
	public boolean handleMessage(MessageContext arg0) {
		return true;
	}

}
