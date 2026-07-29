/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.xml.transform.Source;

import org.w3c.dom.Element;

import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.server.Container;
import com.sun.xml.ws.api.server.InstanceResolver;
import com.sun.xml.ws.api.server.Invoker;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.server.EndpointFactory;
import com.sun.xml.ws.transport.http.server.EndpointImpl;

import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.EndpointContext;
import jakarta.xml.ws.EndpointReference;
import jakarta.xml.ws.handler.Handler;
import jakarta.xml.ws.spi.http.HttpContext;

/**
 * Best-effort workaround for a defect in the Eclipse Metro JAX-WS reference
 * implementation (jaxws-rt) where {@link Endpoint#publish(String)} (and the
 * other {@code publish} overloads) unconditionally re-processes the
 * implementor's static {@code @jakarta.jws.HandlerChain} annotation during
 * publish and, in doing so, silently discards any handler chain that was
 * already set programmatically (e.g. dynamic whiteboard handlers) before
 * publish was called.
 * <p>
 * See <a href="https://github.com/eclipse-ee4j/metro-jax-ws/issues/812">
 * metro-jax-ws#812</a> for the upstream issue.
 * <p>
 * The merged handler chain itself is computed independently of this class
 * (see {@link StaticHandlerChain}), so it is available regardless of
 * whether the JAX-WS implementation in use is affected by this defect. This
 * class only works around actually getting that merged chain to take effect
 * at publish time on Metro/jaxws-rt, by calling
 * {@link EndpointFactory#createEndpoint} directly with
 * {@code processHandlerAnnotation=false} (so the RI never overwrites our
 * merged chain) and wrapping the resulting {@link WSEndpoint} using one of
 * the public (if deprecated) "backdoor" constructors of {@link EndpointImpl},
 * which publish an already-built {@code WSEndpoint} without going through
 * the buggy code path.
 * <p>
 * This bundle has a compile-time-only ({@code provided} scope) dependency on
 * jaxws-rt for these classes, but no hard runtime dependency: the
 * corresponding packages are imported with {@code resolution:=optional} (see
 * {@code bnd.bnd}), so on OSGi frameworks where jaxws-rt (or a different
 * JAX-WS implementation, or an incompatible jaxws-rt version) does not
 * export them, this class simply fails to link (e.g. with a
 * {@link LinkageError}) the first time one of its methods is invoked.
 * Callers should catch that, log a warning that the handler chain may be
 * incomplete, and fall back to the standard {@link Endpoint#create(Object)}
 * best-effort approach with {@link jakarta.xml.ws.Binding#setHandlerChain}.
 */
final class MetroHandlerChainWorkaround {

    private MetroHandlerChainWorkaround() {
    }

    /**
     * Attempts to create an {@link Endpoint} for the given implementor whose
     * handler chain is the given (already merged) list, bypassing the RI's
     * own {@code @HandlerChain} reprocessing so the merged chain actually
     * takes effect at publish time.
     *
     * @param implementor the webservice implementor instance
     * @param mergedChain the already-merged (static + dynamic) handler chain
     *                    to install, e.g. as computed by
     *                    {@link StaticHandlerChain#merge}
     * @return an {@link Endpoint} ready to be published via
     *         {@link Endpoint#publish(String)} or
     *         {@link Endpoint#publish(HttpContext)}
     */
    static Endpoint tryCreate(Object implementor, List<Handler> mergedChain) {
        Class<?> implClass = implementor.getClass();

        BindingID bindingId = BindingID.parse(implClass);
        WSBinding binding = bindingId.createBinding();
        binding.setHandlerChain(mergedChain);

        Invoker invoker = InstanceResolver.createSingleton(implementor).createInvoker();

        WSEndpoint<?> wsEndpoint = EndpointFactory.createEndpoint(implClass, false, invoker, null, null,
                Container.NONE, binding, null, List.of(), null, false);

        return new DelegatingEndpoint(implementor, binding, wsEndpoint);
    }

    /**
     * A {@link Endpoint} that stores metadata/properties/executor locally
     * until {@link #publish(String)}/{@link #publish(Object)}/
     * {@link #publish(HttpContext)} is called, at which point it constructs
     * the real (already published) jaxws-rt {@link EndpointImpl} using the
     * pre-built {@link WSEndpoint} via one of its "backdoor" constructors,
     * and delegates to it from then on.
     */
    private static final class DelegatingEndpoint extends Endpoint {

        private final Object implementor;
        private final WSBinding binding;
        private final WSEndpoint<?> wsEndpoint;

        private Map<String, Object> properties = Map.of();
        private List<Source> metadata;
        private Executor executor;
        private EndpointContext endpointContext;
        private Endpoint delegate;

        DelegatingEndpoint(Object implementor, WSBinding binding, WSEndpoint<?> wsEndpoint) {
            this.implementor = implementor;
            this.binding = binding;
            this.wsEndpoint = wsEndpoint;
        }

        @Override
        public synchronized jakarta.xml.ws.Binding getBinding() {
            return delegate != null ? delegate.getBinding() : binding;
        }

        @Override
        public Object getImplementor() {
            return implementor;
        }

        @SuppressWarnings("deprecation")
        @Override
        public synchronized void publish(String address) {
            checkNotPublished();
            delegate = new EndpointImpl(wsEndpoint, address, endpointContext);
        }

        @SuppressWarnings("deprecation")
        @Override
        public synchronized void publish(Object serverContext) {
            checkNotPublished();
            delegate = new EndpointImpl(wsEndpoint, serverContext, endpointContext);
        }

        @SuppressWarnings("deprecation")
        @Override
        public synchronized void publish(HttpContext serverContext) {
            checkNotPublished();
            delegate = new EndpointImpl(wsEndpoint, serverContext, endpointContext);
        }

        private void checkNotPublished() {
            if (delegate != null) {
                throw new IllegalStateException("Cannot publish this endpoint. Endpoint has been already published.");
            }
        }

        @Override
        public synchronized void stop() {
            if (delegate != null) {
                delegate.stop();
                delegate = null;
            }
        }

        @Override
        public synchronized boolean isPublished() {
            return delegate != null && delegate.isPublished();
        }

        @Override
        public synchronized List<Source> getMetadata() {
            return metadata;
        }

        @Override
        public synchronized void setMetadata(List<Source> metadata) {
            this.metadata = metadata;
        }

        @Override
        public synchronized Executor getExecutor() {
            return executor;
        }

        @Override
        public synchronized void setExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
        public synchronized Map<String, Object> getProperties() {
            return properties;
        }

        @Override
        public synchronized void setProperties(Map<String, Object> properties) {
            this.properties = Objects.requireNonNull(properties);
        }

        @Override
        public synchronized EndpointReference getEndpointReference(Element... referenceParameters) {
            if (delegate == null) {
                throw new IllegalStateException("Endpoint is not published yet");
            }
            return delegate.getEndpointReference(referenceParameters);
        }

        @Override
        public synchronized <T extends EndpointReference> T getEndpointReference(Class<T> clazz,
                Element... referenceParameters) {
            if (delegate == null) {
                throw new IllegalStateException("Endpoint is not published yet");
            }
            return delegate.getEndpointReference(clazz, referenceParameters);
        }

        @Override
        public synchronized void setEndpointContext(EndpointContext ctxt) {
            this.endpointContext = ctxt;
        }
    }
}
