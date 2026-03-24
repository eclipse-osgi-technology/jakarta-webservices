# Jakarta Web Services Whiteboard
This repository contains an OSGi Whiteboard implementation for Jakarta XML Web Services (JAX-WS), enabling dynamic registration and publishing of SOAP web service endpoints in an OSGi runtime.


## Project Structure

The project consists of two runtime modules and an integration test module.

### Main Artifacts

- **Endpoint Registrar** - Core whiteboard implementation that discovers and manages JAX-WS endpoint services
    - `org.eclipse.osgi-technology.webservices:org.eclipse.osgi.technology.webservices.runtime.registrar`
    - Tracks endpoint implementors and handler extensions registered as OSGi services
    - Publishes endpoints through pluggable `EndpointPublisher` implementations
    - Includes a generic publisher that uses `Endpoint.publish(address)` as a fallback
    - Provides runtime introspection via `WebserviceServiceRuntime` DTOs

- **HTTP Whiteboard Publisher** - Publishes JAX-WS endpoints as servlets using the OSGi HTTP Whiteboard Service
    - `org.eclipse.osgi-technology.webservices:org.eclipse.osgi.technology.webservices.httpwhiteboard`
    - Registers endpoints as Jakarta Servlets through the HTTP Whiteboard
    - Higher-ranked than the generic publisher, preferred when HTTP Whiteboard is available

### Service Properties

Endpoints and handlers are configured via OSGi service properties:

| Property | Description |
|---|---|
| `osgi.service.webservice.endpoint.implementor` | Marks a service as a JAX-WS endpoint implementor |
| `osgi.service.webservice.endpoint.address` | Address for generic publishing (e.g. `http://localhost:8080/service`) |
| `osgi.service.webservice.endpoint.http.contextPath` | Servlet context path for HTTP Whiteboard publishing (e.g. `/myservice`) |
| `osgi.service.webservice.handler.extension` | Marks a service as a message handler extension |
| `osgi.service.webservice.handler.filter` | LDAP filter to selectively bind a handler to matching endpoints |

### Extensibility

Custom publishing strategies can be provided by implementing the `EndpointPublisher` SPI interface and registering it as an OSGi service. Publishers are selected by service ranking, with the highest-ranked compatible publisher used for each endpoint.

## How to build

1. Fetch the code
2. Run `mvn verify`

## How to contribute

Open a PR and make sure to run the following command first to ensure all formatting rules are applied:

```bash
mvn editorconfig:format
```

Also check for javadoc errors with:

```bash
mvn package -Pjavadoc-check
```
