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

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

import jakarta.xml.ws.handler.Handler;

/**
 * Simulate that a handler service can not be fetched
 */
@SuppressWarnings("rawtypes")
public class BadHandler implements ServiceFactory<Handler> {

	@Override
	public Handler getService(Bundle bundle,
			ServiceRegistration<Handler> registration) {
		return null;
	}

	@Override
	public void ungetService(Bundle bundle,
			ServiceRegistration<Handler> registration, Handler service) {
	}

}
