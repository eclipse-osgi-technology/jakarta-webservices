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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import jakarta.jws.HandlerChain;
import jakarta.xml.ws.handler.Handler;

/**
 * Reads the static handler chain declared by a webservice implementor via
 * the {@code @jakarta.jws.HandlerChain} annotation, and merges it with the
 * dynamic (whiteboard) handlers.
 * <p>
 * This is a small, self-contained implementation of the relevant parts of
 * the JSR-181/Jakarta XML Web Services "handler-chains" XML schema (only
 * {@code <handler-chain>}/{@code <handler>}/{@code <handler-class>} are
 * evaluated; optional filtering elements such as {@code <protocol-bindings>}
 * or {@code <port-name-pattern>} are not). It deliberately does
 * <strong>not</strong> depend on any particular JAX-WS implementation (e.g.
 * jaxws-rt/Metro), so the merged chain this class computes is available
 * regardless of whether {@link MetroHandlerChainWorkaround} is applicable to
 * the JAX-WS implementation in use.
 * <p>
 * See <a href="https://github.com/eclipse-ee4j/metro-jax-ws/issues/812">
 * metro-jax-ws#812</a> for the reason merging static and dynamic handlers
 * ourselves is necessary at all: the Metro reference implementation
 * unconditionally re-processes the static {@code @HandlerChain} annotation
 * at publish time, silently discarding any handler chain set beforehand.
 */
final class StaticHandlerChain {

    private StaticHandlerChain() {
    }

    /**
     * Checks whether the given implementor class (or one of its
     * superclasses) declares a static {@code @jakarta.jws.HandlerChain}.
     *
     * @param implClass the implementor class
     * @return {@code true} if a static handler chain is declared
     */
    static boolean isPresent(Class<?> implClass) {
        return declaringClass(implClass) != null;
    }

    private static Class<?> declaringClass(Class<?> implClass) {
        for (Class<?> c = implClass; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(HandlerChain.class)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Parses the static handler chain declared on the given implementor
     * class (or one of its superclasses) via {@code @HandlerChain},
     * instantiating each declared handler class using its public no-arg
     * constructor.
     *
     * @param implClass the implementor class
     * @return the static handler chain in declaration order, or an empty
     *         list if no {@code @HandlerChain} is declared
     * @throws IOException                  if the referenced handler-chains
     *                                       file cannot be located or read
     * @throws ParserConfigurationException if the XML parser cannot be
     *                                       configured
     * @throws SAXException                 if the handler-chains file cannot
     *                                       be parsed
     * @throws ReflectiveOperationException if a declared handler class
     *                                       cannot be loaded or instantiated
     */
    @SuppressWarnings("rawtypes")
    static List<Handler> parse(Class<?> implClass)
            throws IOException, ParserConfigurationException, SAXException, ReflectiveOperationException {
        Class<?> declaringClass = declaringClass(implClass);
        if (declaringClass == null) {
            return List.of();
        }
        HandlerChain annotation = declaringClass.getAnnotation(HandlerChain.class);
        URL url = resolve(declaringClass, annotation.file());
        if (url == null) {
            throw new IOException("Could not resolve handler-chains file '" + annotation.file() + "' declared on "
                    + declaringClass);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document;
        try (InputStream in = url.openStream()) {
            document = builder.parse(in);
        }

        List<Handler> handlers = new ArrayList<>();
        for (Element handlerChain : childElementsByLocalName(document.getDocumentElement(), "handler-chain")) {
            for (Element handler : childElementsByLocalName(handlerChain, "handler")) {
                for (Element handlerClass : childElementsByLocalName(handler, "handler-class")) {
                    String className = handlerClass.getTextContent().trim();
                    Class<?> clazz = Class.forName(className, true, implClass.getClassLoader());
                    handlers.add((Handler) clazz.getDeclaredConstructor().newInstance());
                }
            }
        }
        return handlers;
    }

    /**
     * Resolves the {@code file} attribute of a {@code @HandlerChain}
     * annotation: an absolute URI is used as-is, otherwise it is resolved as
     * a classpath resource relative to the package of the class that
     * declared the annotation, per the Jakarta XML Web Services
     * specification.
     */
    private static URL resolve(Class<?> declaringClass, String file) throws MalformedURLException {
        try {
            URI uri = new URI(file);
            if (uri.isAbsolute()) {
                return uri.toURL();
            }
        } catch (URISyntaxException ignore) {
            // not a valid URI at all -> treat as a plain relative classpath resource
        }
        return declaringClass.getResource(file);
    }

    private static List<Element> childElementsByLocalName(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(node.getLocalName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    /**
     * Merges the statically declared ({@code @HandlerChain}) handlers with
     * the dynamic whiteboard handlers into a single chain, honouring OSGi
     * {@code service.ranking} semantics as if the static handlers were
     * themselves registered with the default ranking of {@code 0}: handlers
     * are ordered by rank descending, and ties are broken by putting static
     * handlers (in declaration order) before dynamic handlers of the same
     * rank (which are themselves already ordered by
     * {@link HandlerInfo#SORT_BY_PRIORITY}).
     *
     * @param staticChain         the handlers declared via
     *                            {@code @HandlerChain}, in declaration order
     * @param dynamicHandlerInfos the dynamic (whiteboard) handlers, already
     *                            filtered/sorted by priority
     * @return the merged handler chain
     */
	@SuppressWarnings("rawtypes")
	static List<Handler> merge(List<Handler> staticChain, List<HandlerInfo> dynamicHandlerInfos) {
        record RankedHandler(Handler handler, int rank, int group, int index) {
        }

        List<RankedHandler> ranked = new ArrayList<>(staticChain.size() + dynamicHandlerInfos.size());
        int index = 0;
        for (Handler h : staticChain) {
            // static handlers behave as if registered with the default service ranking
            ranked.add(new RankedHandler(h, 0, 0, index++));
        }
        index = 0;
        for (HandlerInfo info : dynamicHandlerInfos) {
            Handler<?> h = info.fetchHandler();
            if (h != null) {
                ranked.add(new RankedHandler(h, info.getServiceRank(), 1, index++));
            }
        }
        return ranked.stream()
                .sorted(Comparator.comparingInt(RankedHandler::rank).reversed()
                        .thenComparingInt(RankedHandler::group).thenComparingInt(RankedHandler::index))
                .map(RankedHandler::handler).toList();
    }
}
