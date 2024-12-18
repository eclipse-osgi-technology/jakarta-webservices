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
package org.eclipse.osgi.technology.webservices.spi;

import org.osgi.annotation.versioning.ProviderType;

import jakarta.xml.ws.Endpoint;

/**
 * An SPI interface implemented by components that are capable of publishing an
 * endpoints.
 */
@ProviderType
public interface EndpointPublisher {

    /**
     * Called by the Endpoint Registrar to publish an endpoint, if this publisher is
     * capable of publishing the endpoint given the provided properties it must
     * return a {@link PublishedEndpoint} that can be used to unpublish it again. If
     * the method throw any {@link RuntimeException} the endpoint is assumed to
     * failed to publish
     *
     * @param endpoint the endpoint to publish
     * @return the published endpoint
     * @throws RuntimeException when the endpoint is failing to publish because it
     *                          is invalid
     */
    PublishedEndpoint publishEndpoint(Endpoint endpoint);
}
