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

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;
import jakarta.jws.soap.SOAPBinding.Style;

/**
 * A simple echo webservice we use here for testing purpose
 */
@WebService(name = "Echo")
@SOAPBinding(style = Style.RPC)
public class WSEcho {

	/**
	 * THe constructor
	 */
	public WSEcho() {
		System.out.println("I will echo everything I receive!");
	}

	/**
	 * A webservice annotated method
	 *
	 * @param text the text to echo
	 * @return the same as before
	 */
	@WebMethod(operationName = "echo", action = "echo")
	public String echo(@WebParam(name = "textIn") String text) {
		System.out.println("Echo '" + text + "' to caller!");
		return text;
	}

}
