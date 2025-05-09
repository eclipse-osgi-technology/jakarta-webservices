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
package org.eclipse.osgi.technology.webservices.integration.tests.binding;

import java.net.URL;

import javax.xml.namespace.QName;

import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebEndpoint;
import jakarta.xml.ws.WebServiceClient;

/**
 * A simple echo extending {@link Service}
 */
@WebServiceClient(name = "WSEchoService", targetNamespace = WSEchoService.NS)
public class WSEchoService extends Service {

    static final String NS = "http://implementor.tests.integration.webservices.technology.osgi.eclipse.org/";
    private final static QName WSECHOSERVICE_QNAME = new QName(NS, "WSEchoService");

    /**
     * constructor
     *
     * @param wsdlLocation location
     */
    public WSEchoService(URL wsdlLocation) {
        super(wsdlLocation, WSECHOSERVICE_QNAME);
    }

    /**
     * An endpoint
     *
     * @return echos
     */
    @WebEndpoint(name = "EchoPort")
    public Echo getEchoPort() {
        return super.getPort(new QName(NS, "EchoPort"), Echo.class);
    }

}
