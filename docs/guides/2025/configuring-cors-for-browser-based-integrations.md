# Configuring CORS for Browser-Based Integrations

- Date: March 8, 2026
- Topic: Configuring CORS for custom browser applications that integrate with the Apicurio Registry
  REST API

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Understanding the Problem](#2-understanding-the-problem)
3. [Apicurio Registry CORS Defaults](#3-apicurio-registry-cors-defaults)
4. [Configuration Reference (Environment Variables)](#4-configuration-reference-environment-variables)
5. [Scenario 1: Local Development (No Authentication)](#5-scenario-1-local-development-no-authentication)
6. [Scenario 2: Production with Specific Origins](#6-scenario-2-production-with-specific-origins)
7. [Scenario 3: Production with Authentication (Keycloak)](#7-scenario-3-production-with-authentication-keycloak)
8. [Kubernetes Operator Configuration](#8-kubernetes-operator-configuration)
9. [Troubleshooting](#9-troubleshooting)
10. [Additional Resources](#10-additional-resources)

---

## 1. Introduction

If you are building a browser-based application (React, Angular, Vue, plain JavaScript, etc.) that
calls the Apicurio Registry REST API, you will likely encounter **CORS** (Cross-Origin Resource
Sharing) errors. This guide explains what CORS is, why the default Registry configuration may block
your requests, and how to configure CORS to allow your application to communicate with the Registry
API.

### What is CORS?

Browsers enforce the **same-origin policy**, which prevents a web page served from one origin (e.g.
`https://my-app.example.com`) from making HTTP requests to a different origin (e.g.
`https://registry.example.com`). CORS is the mechanism by which a server explicitly allows
cross-origin requests by including specific HTTP response headers such as
`Access-Control-Allow-Origin`.

Without the correct CORS configuration on the Registry server, the browser will block API calls from
your application, even though the same requests would succeed from a non-browser client like `curl`.

---

## 2. Understanding the Problem

Consider this typical scenario:

- Your browser application is served from `https://my-app.example.com`
- Apicurio Registry is running at `https://registry.example.com`

When your JavaScript code calls the Registry API:

```javascript
fetch("https://registry.example.com/apis/registry/v3/groups")
```

The browser first sends a **preflight** `OPTIONS` request to the Registry, asking whether the
cross-origin request is allowed. The Registry must respond with:

- `Access-Control-Allow-Origin` matching your application's origin
- `Access-Control-Allow-Methods` listing the HTTP methods you use
- `Access-Control-Allow-Headers` listing any custom headers your request includes

If the Registry does not respond with the correct headers, the browser blocks the request and you
see an error like:

```
Access to fetch at 'https://registry.example.com/apis/registry/v3/groups'
from origin 'https://my-app.example.com' has been blocked by CORS policy:
No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

---

## 3. Apicurio Registry CORS Defaults

Apicurio Registry ships with CORS enabled by default, configured for the bundled UI. The default
settings (from `application.properties`) are:

| Property                     | Default Value                                                  |
|------------------------------|----------------------------------------------------------------|
| `quarkus.http.cors`          | `true`                                                         |
| `quarkus.http.cors.origins`  | `http://localhost:8888,http://127.0.0.1:8888`                  |
| `quarkus.http.cors.methods`  | `GET,PUT,POST,PATCH,DELETE,OPTIONS`                            |
| `quarkus.http.cors.headers`  | `x-registry-name,x-registry-name-encoded,x-registry-description,x-registry-description-encoded,x-registry-version,x-registry-artifactid,x-registry-artifacttype,x-registry-hash-algorithm,x-registry-content-hash,access-control-request-method,access-control-allow-credentials,access-control-allow-origin,access-control-allow-headers,authorization,content-type,content-encoding,user-agent` |

### Why the defaults don't work for custom applications

The default allowed origins (`localhost:8888` and `127.0.0.1:8888`) match the bundled Registry UI.
If your application runs on a different host, port, or protocol, the Registry will reject preflight
requests from your origin. You need to add your application's origin to the allowed list.

---

## 4. Configuration Reference (Environment Variables)

You can override the default CORS settings using environment variables. This is the recommended
approach when running the Registry in a container.

| Environment Variable          | Description                                      | Example                                         |
|-------------------------------|--------------------------------------------------|-------------------------------------------------|
| `QUARKUS_HTTP_CORS`           | Enable or disable CORS entirely                  | `true`                                          |
| `QUARKUS_HTTP_CORS_ORIGINS`   | Comma-separated list of allowed origins, or `*`  | `https://my-app.example.com,https://other.example.com` |
| `QUARKUS_HTTP_CORS_METHODS`   | Comma-separated list of allowed HTTP methods     | `GET,PUT,POST,PATCH,DELETE,OPTIONS`             |
| `QUARKUS_HTTP_CORS_HEADERS`   | Comma-separated list of allowed request headers  | `authorization,content-type`                    |

> **Note:** Setting `QUARKUS_HTTP_CORS_ORIGINS` via an environment variable completely overrides the
> default origins from `application.properties`. The default methods and headers will still apply
> unless you also override them.

For the full set of Quarkus CORS options, see the
[Quarkus HTTP Reference](https://quarkus.io/guides/http-reference#cors-filter).

---

## 5. Scenario 1: Local Development (No Authentication)

During local development, the simplest approach is to allow all origins using `*`.

### Docker Compose

A ready-to-use Docker Compose file is provided at
[`docker-compose-no-auth.yaml`](configuring-cors-for-browser-based-integrations/docker-compose-no-auth.yaml):

```yaml
services:
  apicurio-registry:
    image: apicurio/apicurio-registry:latest-snapshot
    environment:
      QUARKUS_HTTP_CORS_ORIGINS: "*"
    ports:
      - "8080:8080"

  apicurio-registry-ui:
    image: apicurio/apicurio-registry-ui:latest-snapshot
    ports:
      - "8888:8080"
    depends_on:
      - apicurio-registry
```

Start it with:

```bash
docker compose -f docker-compose-no-auth.yaml up
```

### Testing from JavaScript

With the Registry running on `localhost:8080`, you can call the API from any origin:

```javascript
// List all groups in the registry
const response = await fetch("http://localhost:8080/apis/registry/v3/groups");
const data = await response.json();
console.log(data);
```

> **Warning:** Using `QUARKUS_HTTP_CORS_ORIGINS: "*"` is acceptable for local development but
> should **not** be used in production. A wildcard origin allows any website to make requests to
> your Registry instance.

---

## 6. Scenario 2: Production with Specific Origins

In production, you should explicitly list the origins that are allowed to access the Registry API.

### Single Origin

```yaml
services:
  apicurio-registry:
    image: apicurio/apicurio-registry:latest-snapshot
    environment:
      QUARKUS_HTTP_CORS_ORIGINS: "https://my-app.example.com"
    ports:
      - "8080:8080"
```

### Multiple Origins

Separate multiple origins with commas:

```yaml
services:
  apicurio-registry:
    image: apicurio/apicurio-registry:latest-snapshot
    environment:
      QUARKUS_HTTP_CORS_ORIGINS: "https://my-app.example.com,https://admin.example.com"
    ports:
      - "8080:8080"
```

> **Tip:** Remember to include the bundled Registry UI origin if you still want to access it.
> For example: `https://my-app.example.com,https://registry-ui.example.com`

---

## 7. Scenario 3: Production with Authentication (Keycloak)

When using OIDC authentication (e.g. Keycloak), CORS and authentication work together. Your
browser application must send an `Authorization` header with a bearer token, and the Registry's
CORS configuration must allow that header.

The good news is that the `authorization` header is already included in the default allowed headers,
so you typically only need to configure the allowed origins.

### Docker Compose with Keycloak

A ready-to-use Docker Compose file is provided at
[`docker-compose-with-auth.yaml`](configuring-cors-for-browser-based-integrations/docker-compose-with-auth.yaml):

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:23.0.7
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command:
      - start-dev
      - --import-realm
    ports:
      - 8080:8080
    volumes:
      - ./realm.json:/opt/keycloak/data/import/realm.json

  apicurio-registry:
    image: apicurio/apicurio-registry:latest-snapshot
    environment:
      QUARKUS_OIDC_TENANT_ENABLED: "true"
      QUARKUS_OIDC_AUTH_SERVER_URL: "http://keycloak:8080/realms/registry"
      QUARKUS_OIDC_CLIENT_ID: "registry-api"
      QUARKUS_HTTP_CORS_ORIGINS: "https://my-app.example.com"
    ports:
      - "8081:8080"
    depends_on:
      - keycloak
```

### Key Points for CORS with Authentication

- The `authorization` header is included in the default CORS allowed headers. You do not need to
  add it manually unless you override `QUARKUS_HTTP_CORS_HEADERS`.
- If you override `QUARKUS_HTTP_CORS_HEADERS`, make sure to include `authorization` in your list.
- When using `QUARKUS_HTTP_CORS_ORIGINS: "*"` with credentials, some browsers will reject the
  request. Use explicit origins when authentication is involved.

> **Note:** Authentication is a complex topic with many possible configurations (Keycloak, Microsoft
> Entra ID, other OIDC providers). This section covers only the CORS-specific aspects. For a
> complete guide to configuring authentication, see the
> [Apicurio Registry security documentation](https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-configuring-the-registry.html).

---

## 8. Kubernetes Operator Configuration

When deploying Apicurio Registry using the Kubernetes Operator, you can set CORS environment
variables in the `ApicurioRegistry3` custom resource.

### Explicit Origins

```yaml
apiVersion: registry.apicur.io/v1
kind: ApicurioRegistry3
metadata:
  name: my-registry
spec:
  app:
    env:
      - name: QUARKUS_HTTP_CORS_ORIGINS
        value: "https://my-app.example.com,https://admin.example.com"
```

### Automatic Origin Detection

The Apicurio Registry Operator has built-in logic for CORS origin configuration:

1. If you set `QUARKUS_HTTP_CORS_ORIGINS` in the `spec.app.env` section, those origins are used.
2. If no origins are configured, the operator automatically detects the UI Ingress host and adds
   both `http://` and `https://` variants of that host to the allowed origins.
3. If no Ingress host is found either, the operator defaults to `*` (allow all origins).

This means that in many Kubernetes deployments, **you may not need to configure CORS at all** — the
operator handles it automatically based on your Ingress configuration.

---

## 9. Troubleshooting

### "No 'Access-Control-Allow-Origin' header is present on the requested resource"

**Cause:** Your application's origin is not in the Registry's allowed origins list.

**Fix:** Add your application's origin to `QUARKUS_HTTP_CORS_ORIGINS`. Make sure to include the
protocol and port (e.g. `https://my-app.example.com:3000`, not just `my-app.example.com`).

### Preflight request fails with 403

**Cause:** The HTTP method or a request header is not allowed by the CORS configuration.

**Fix:** Check that `QUARKUS_HTTP_CORS_METHODS` includes the HTTP method your application uses, and
that `QUARKUS_HTTP_CORS_HEADERS` includes any custom headers your application sends.

### CORS works without authentication but fails with it

**Cause:** The `authorization` header may not be in the allowed headers list (this can happen if you
override `QUARKUS_HTTP_CORS_HEADERS` without including `authorization`).

**Fix:** If you have overridden `QUARKUS_HTTP_CORS_HEADERS`, make sure `authorization` is in the
list.

### Credentials/cookies are not sent

**Cause:** When using `Access-Control-Allow-Origin: *`, browsers will not send credentials
(cookies, authorization headers) in some configurations.

**Fix:** Use explicit origins instead of `*` when your application needs to send credentials.

### Using Browser Developer Tools to Diagnose CORS Issues

1. Open your browser's Developer Tools (F12)
2. Go to the **Network** tab
3. Look for the failed request — it will often show as "(blocked:cors)" or have a red status
4. Look for a preceding `OPTIONS` request (the preflight) and inspect its response headers
5. Check whether `Access-Control-Allow-Origin` is present and matches your application's origin
6. Check whether `Access-Control-Allow-Methods` and `Access-Control-Allow-Headers` include the
   values your application needs

---

## 10. Additional Resources

- [Quarkus HTTP CORS Filter Documentation](https://quarkus.io/guides/http-reference#cors-filter) —
  full reference for all CORS configuration options
- [Apicurio Registry Security Configuration](https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-configuring-the-registry.html) —
  configuring authentication and authorization
- [MDN: Cross-Origin Resource Sharing (CORS)](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS) —
  comprehensive explanation of how CORS works in browsers
