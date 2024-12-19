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
package org.eclipse.osgi.technology.webservices.tests.binding;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;
import jakarta.xml.ws.Action;

/**
 * a simple service interface
 */
@WebService(name = "Echo", targetNamespace = "http://webservice.jakartaws.cases.test.osgi.org/")
@SOAPBinding(style = SOAPBinding.Style.RPC)
public interface Echo {

	/**
	 * echo everything we got
	 *
	 * @param textIn input
	 * @return output
	 */
	@WebMethod(action = "echo")
	@WebResult(partName = "return")
	@Action(input = "echo", output = "http://webservice.jakartaws.cases.test.osgi.org/Echo/echoResponse")
	public String echo(@WebParam(name = "textIn", partName = "textIn")
	String textIn);

}
