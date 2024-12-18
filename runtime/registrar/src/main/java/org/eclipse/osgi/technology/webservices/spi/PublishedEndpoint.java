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

/**
 * Interface to control the endpoint registration and to get access to the full
 * published URI
 */
@ProviderType
public interface PublishedEndpoint {

    /**
     * Unpublish the endpoint
     */
    void unpublish();

    /**
     * @return the full address under that this endpoint is reachable
     */
    String getAddress();

}
