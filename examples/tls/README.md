# TLS

A complete, local example of serving Apicurio Registry over TLS, and of every client checking it:

- **`registry`** serves HTTPS with a PKCS12 key store, redirects plain HTTP to HTTPS, and
  authenticates callers with HTTP basic, so credentials only travel encrypted.
- **`registry-mtls`** serves HTTPS only, with PEM files, behind **`mtls-proxy`**, an nginx that
  requires a client certificate signed by the example CA (mutual TLS).
- **curl, the Java SDK, the Kafka serializers and the Maven plugin** verify the server certificate
  with PKCS12, JKS and PEM trust stores, and present client certificates from PKCS12, JKS and PEM
  key stores. Every refusal is checked too: no or wrong CA, wrong host name, no or untrusted client
  certificate, wrong password.
- **The certificates are generated on your machine** by [`certs/generate-certs.sh`](certs/generate-certs.sh)
  (openssl and keytool). No private key is committed.

**New to this? Follow the [step-by-step guide](GUIDE.md).** It walks through every check, with
commands and expected output, in about 30 minutes.

## Quick start

Requires Docker (or Podman) with Compose v2, `curl`, `jq`, `openssl` and JDK 21 or later.

```bash
./demo.sh    # generates the certificates, starts the containers, builds the clients, checks
```

`demo.sh` generates the certificates if `certs/` has none, runs `docker compose up -d`, builds the
Java clients and the Maven plugin from this checkout on its first run ([`build.sh`](build.sh), a few
minutes), then prints one line per check and ends with `All checks passed.` (it exits non-zero if a
check fails). Run it again at any time.

Then call the registries yourself:

```bash
curl --cacert certs/ca-cert.pem -u developer:developer https://localhost:8443/apis/registry/v3/system/info
curl --cacert certs/ca-cert.pem --cert certs/client-cert.pem --key certs/client-key.pem \
  https://localhost:9443/apis/registry/v3/system/info
```

Stop and remove everything with `docker compose down -v`. Delete the certificates with
`rm certs/*.pem certs/*.p12 certs/*.jks` (the next `demo.sh` makes new ones).

## Services

| Service | URL | Purpose |
|---|---|---|
| `registry` | https://localhost:8443 (http://localhost:8080 redirects) | HTTPS, PKCS12 key store, HTTP basic (`developer` / `developer`) |
| `mtls-proxy` -> `registry-mtls` | https://localhost:9443 | Mutual TLS: a client certificate is required |
| `kafka` | localhost:9092 | For the serializers (plain Kafka; only the registry calls use TLS) |

## Files

| File | Purpose |
|---|---|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`docker-compose.yml`](docker-compose.yml) | The services; the TLS configuration is in each registry's `environment` |
| [`certs/generate-certs.sh`](certs/generate-certs.sh) | Generates the CA, server and client certificates, and the stores, in `certs/` |
| [`mtls-proxy/nginx.conf`](mtls-proxy/nginx.conf) | The mutual TLS front of `registry-mtls` |
| [`demo.sh`](demo.sh) | Generates, starts, builds, and checks the expected behaviour |
| [`build.sh`](build.sh) | Builds the clients and the Maven plugin from this checkout |
| [`clients/pom.xml`](clients/pom.xml) | The Maven plugin's TLS configurations, one execution each |
| [`clients/.../Clients.java`](clients/src/main/java/io/apicurio/registry/examples/tls/Clients.java) | The Java SDK and the Kafka serdes over TLS and mutual TLS |
| [`ci.env`](ci.env) | CI settings: `demo.sh` starts the containers itself, after making the certificates |

## Server configuration

The registry uses the [Quarkus TLS registry](https://quarkus.io/guides/tls-registry-reference):

| Setting | Meaning |
|---|---|
| `QUARKUS_TLS_KEY_STORE_P12_PATH`, `QUARKUS_TLS_KEY_STORE_P12_PASSWORD` | The certificate and key as a PKCS12 key store (`registry`) |
| `QUARKUS_TLS_KEY_STORE_PEM_<name>_CERT`, `QUARKUS_TLS_KEY_STORE_PEM_<name>_KEY` | The certificate and key as PEM files (`registry-mtls`). Use a name, not a number: see the notes |
| `QUARKUS_TLS_KEY_STORE_JKS_PATH`, `QUARKUS_TLS_KEY_STORE_JKS_PASSWORD` | The same as a JKS key store (not used here) |
| `QUARKUS_HTTP_INSECURE_REQUESTS` | Plain HTTP: `enabled` (the default), `redirect` (to HTTPS) or `disabled` (port 8080 closed) |
| `QUARKUS_HTTP_SSL_PORT` | The HTTPS port, 8443 by default |

With a key store set, HTTPS listens on 8443, and the management interface (port 9000: health and
metrics) serves HTTPS too, with the same certificate. The older `QUARKUS_HTTP_SSL_CERTIFICATE_*`
properties (for example `QUARKUS_HTTP_SSL_CERTIFICATE_KEY_STORE_FILE`) still work, but the TLS
registry is the current way.

## Mutual TLS

The registry image cannot require client certificates itself: `quarkus.http.ssl.client-auth` is
fixed when the image is built (to `NONE`), and setting `QUARKUS_HTTP_SSL_CLIENT_AUTH=required` at
runtime only logs `Build time property cannot be changed at runtime`. So mutual TLS is enforced in
front of the registry, here by nginx (`ssl_verify_client on`), which then calls the registry over TLS
and checks its certificate too. On Kubernetes, the same proxy runs as a sidecar in the registry pod,
or the ingress or service mesh does it.

The clients are configured the same way whatever terminates mutual TLS:

| Client | Trust store (server certificate) | Key store (client certificate) |
|---|---|---|
| Java SDK (`RegistryClientOptions`) | `trustStorePkcs12`, `trustStoreJks`, `trustStorePem` | `keystorePkcs12`, `keystoreJks`, `keystorePem(cert, key)` |
| Kafka serdes | `apicurio.registry.tls.truststore.location` / `.password` / `.type`, or `apicurio.registry.tls.certificates` (PEM) | `apicurio.registry.tls.keystore.location` / `.password` / `.type`, or `apicurio.registry.tls.client-certificate` and `apicurio.registry.tls.client-key` (PEM) |
| Maven plugin | `trustStorePath`, `trustStorePassword`, `trustStoreType` | `keyStorePath`, `keyStorePassword`, `keyStoreType`, `keyStorePemKeyPath` |
| curl | `--cacert` | `--cert`, `--key` |

For development only, the SDK has `trustAll(true)` and `verifyHost(false)`, the serdes
`apicurio.registry.tls.trust-all` and `apicurio.registry.tls.verify-host`, and the Maven plugin
`trustAll` and `verifyHostname`.

## On Kubernetes

This example replaces `mtls-minikube`, which deployed the same setup to Minikube. Its
Kubernetes-specific parts are covered elsewhere:

- **The server certificate:** the operator's `spec.app.tls` (`keystoreSecretRef`,
  `keystorePasswordSecretRef`, `truststoreSecretRef`, `insecureRequests`) mounts a PKCS12 key store
  from a Secret and sets the same `QUARKUS_TLS_KEY_STORE_P12_*` and `QUARKUS_HTTP_INSECURE_REQUESTS`
  as `registry` here; it also exposes Service port 443 and switches the probes to HTTPS. See
  *Deploying Registry using the Operator*
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-deploying-registry-operator.adoc)),
  the operator's [HTTPS templates](../../operator/docs/modules/ROOT/examples/templates/https/)
  (edge and passthrough, with cert-manager) and the [operator example](../operator/).
- **Mutual TLS:** run an nginx sidecar with `spec.app.podTemplateSpec`, configured like
  [`mtls-proxy/nginx.conf`](mtls-proxy/nginx.conf) but adapted to a shared pod network: the sidecar
  listens on the Service's HTTPS port, and the registry listens on a different port bound to
  loopback (for example `QUARKUS_HTTP_HOST=127.0.0.1` and `QUARKUS_HTTP_SSL_PORT=8444`), so only the
  sidecar reaches it. Point the upstream at `https://127.0.0.1:8444`, with `proxy_ssl_name` set to a
  name in the registry's certificate when it verifies it. Mount the certificates from a Secret.
- **Client certificates for applications:** a Secret mounted into the client pod; the client
  settings are the same as above.

## Notes

- **Other ports:** if 8443, 8080, 9443 or 9092 are in use, see [Using other ports](GUIDE.md#using-other-ports).
- **Local build:** the example uses the `latest-snapshot` image. To run a locally built registry,
  build it (`./mvnw install -pl distro/docker -am -DskipTests`), build
  `distro/docker/target/docker/Dockerfile.jvm` as an image, and run
  `REGISTRY_IMAGE=<that image> ./demo.sh`.
- **PEM key stores in environment variables:** name the pair (`QUARKUS_TLS_KEY_STORE_PEM_SERVER_CERT`).
  With a number (`QUARKUS_TLS_KEY_STORE_PEM_0_CERT`, as `quarkus.tls.key-store.pem.0.cert` is often
  written), the setting is ignored and the registry starts without HTTPS. As a system property
  (`JAVA_OPTS_APPEND=-Dquarkus.tls.key-store.pem.0.cert=...`), `0` works.
- **Client errors:** the Maven plugin reports a proxy's HTML error without its status code, and the
  SDK reports a wrong password as an `ApiException` with no body ([#11261](https://github.com/Apicurio/apicurio-registry/issues/11261)).
- **Local only:** the CA is generated on your machine and its key is deleted, but the store password
  (`apicurio`) and the user's password are in plain text, `registry-mtls` keeps an in-memory
  database, and the other services in the Compose project can call `registry-mtls` without the
  proxy. In production, use certificates from your CA or cert-manager, keep passwords in secrets,
  and restrict who can reach the registry (the operator creates a NetworkPolicy).
- **OAuth2 over TLS:** the plugin's `authServerUrl`, `clientId`, `clientSecret` and `clientScope`
  combine with the TLS settings shown here; the [authentication example](../authentication/) runs
  them against Keycloak.

## Learn more

- *Managing artifacts with the Maven plugin*: TLS and mutual TLS options
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-managing-registry-artifacts-maven.adoc)).
- *Using the Registry client*: TLS configuration
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-using-the-registry-client.adoc)).
- *Configuring Kafka client SerDes*: TLS security for the registry connection
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-configuring-kafka-client-serdes.adoc)).
- [Quarkus TLS registry reference](https://quarkus.io/guides/tls-registry-reference) and
  [HTTP reference, SSL](https://quarkus.io/guides/http-reference#ssl).
- [Authentication example](../authentication/) and [ADR-0009](../../adr/0009-authentication.md):
  who the caller is, once the connection is secure.
