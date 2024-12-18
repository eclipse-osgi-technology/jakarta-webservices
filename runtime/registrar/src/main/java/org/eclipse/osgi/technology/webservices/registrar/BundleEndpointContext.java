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
package org.eclipse.osgi.technology.webservices.registrar;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.osgi.framework.Bundle;

import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.EndpointContext;

class BundleEndpointContext extends EndpointContext {

    private Set<Endpoint> endpoints = ConcurrentHashMap.newKeySet();
    private Set<Endpoint> endpointsView = Collections.unmodifiableSet(endpoints);
    private Bundle bundle;

    BundleEndpointContext(Bundle bundle) {
        this.bundle = bundle;
    }

    @Override
    public Set<Endpoint> getEndpoints() {
        return endpointsView;
    }

    @Override
    public String toString() {
        Set<Endpoint> ep = getEndpoints();
        return "Endpoints for bundle " + bundle.getSymbolicName() + " " + bundle.getVersion() + ": "
                + ep.stream().map(Endpoint::getImplementor).map(String::valueOf).collect(
                        Collectors.joining(System.lineSeparator(), System.lineSeparator(), "Total: " + ep.size()));
    }

    void addEndpoint(Endpoint endpoint) {
        endpoints.add(endpoint);
    }

    void removeEndpoint(Endpoint endpoint) {
        endpoints.remove(endpoint);
    }

}
