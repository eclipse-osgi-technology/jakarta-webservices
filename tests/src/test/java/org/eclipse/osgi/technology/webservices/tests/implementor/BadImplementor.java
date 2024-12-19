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
package org.eclipse.osgi.technology.webservices.tests.implementor;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

/**
 * Simulates a hander for which the service can't be fetched
 */
public class BadImplementor implements ServiceFactory<String> {

	@Override
	public String getService(Bundle bundle,
			ServiceRegistration<String> registration) {
		return null;
	}

	@Override
	public void ungetService(Bundle bundle,
			ServiceRegistration<String> registration, String service) {
	}

}
