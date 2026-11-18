# TLS: step-by-step guide

This guide walks through serving Apicurio Registry over TLS, and through every client checking it:
curl, the Java SDK, the Kafka serializers and the Maven plugin, with and without client
certificates. Follow it on your own, or use it as the script for a live demo.

**Time:** about 30 minutes. **You will see:**

- certificates generated for the demo: a CA, a server certificate, a client certificate, and a
  rogue CA nothing trusts;
- a registry serving HTTPS, with plain HTTP redirected or switched off;
- clients refusing a server they cannot verify: no CA, the wrong CA, the wrong host name;
- mutual TLS: a proxy refusing clients without a certificate, or with one from the rogue CA;
- the Maven plugin, the Java SDK and the Kafka serdes with PKCS12, JKS and PEM stores, and with
  credentials.

For the reference documentation, see the [Learn more](README.md#learn-more) links in the README.

## Before you start

You need:

- Docker with Docker Compose v2, or Podman with a Compose provider;
- `curl`, `jq`, `openssl`, and JDK 21 or later (`keytool` makes the JKS stores; `demo.sh` builds the
  clients and the Maven plugin from this checkout);
- free ports 8443, 8080, 9443 and 9092; see [Using other ports](#using-other-ports).

```bash
cd examples/tls
```

## Step 1: Generate the certificates

```bash
./certs/generate-certs.sh
```

| File | What it is |
|---|---|
| `ca-cert.pem` | The example CA. Clients trust it; the proxy accepts client certificates it signed |
| `server-cert.pem`, `server-key.pem`, `server-keystore.p12` | The registries' certificate, for `localhost`, `registry` and `registry-mtls` (not `127.0.0.1`, on purpose) |
| `client-cert.pem`, `client-key.pem`, `client-keystore.p12`, `client-keystore.jks` | A client certificate signed by the example CA |
| `truststore.p12`, `truststore.jks` | The example CA as trust stores |
| `rogue-ca-cert.pem`, `rogue-client-*` | Another CA, and a client certificate it signed: nothing trusts them |

Every store's password is `apicurio`. The CA keys are deleted once everything is signed.

```bash
openssl x509 -in certs/server-cert.pem -noout -subject -issuer -ext subjectAltName
```

## Step 2: Start the example and run the checks

```bash
./demo.sh
```

`demo.sh` runs `docker compose up -d`, waits for the registries, builds the clients on its first run
([`build.sh`](build.sh), a few minutes), then runs every step below and ends with
`All checks passed.` (`docker compose up -d` on its own works too, once `certs/` is filled.)

| Service | Port | TLS |
|---|---|---|
| `registry` | 8443 (HTTPS), 8080 (HTTP) | PKCS12 key store; HTTP redirects; HTTP basic `developer` / `developer` |
| `mtls-proxy` -> `registry-mtls` | 9443 | The proxy requires a client certificate, then calls the registry over HTTPS |
| `kafka` | 9092 | (for the serializers in step 9) |

## Step 3: HTTPS, and a client that checks it

The registry's TLS configuration is two settings ([`docker-compose.yml`](docker-compose.yml)):

```yaml
QUARKUS_TLS_KEY_STORE_P12_PATH: /certs/server-keystore.p12
QUARKUS_TLS_KEY_STORE_P12_PASSWORD: apicurio
```

A client that trusts the example CA gets in:

```bash
curl --cacert certs/ca-cert.pem -u developer:developer https://localhost:8443/apis/registry/v3/system/info
```

```json
{"name": "Apicurio Registry (SQL)", "version": "3.4.0-SNAPSHOT", ...}
```

The certificate it receives was issued by the example CA:

```bash
openssl s_client -connect localhost:8443 -servername localhost < /dev/null 2> /dev/null | openssl x509 -noout -issuer
# issuer=CN=Apicurio Registry Example CA, O=Apicurio
```

Every other attempt fails before any HTTP is sent (curl exit code 60, "SSL certificate problem"):

| Attempt | Why it fails |
|---|---|
| no `--cacert` | The example CA is not among the system's CAs |
| `--cacert certs/rogue-ca-cert.pem` | The certificate was not signed by that CA |
| `https://127.0.0.1:8443` with the right CA | `127.0.0.1` is not a name in the certificate |

TLS does not authenticate the caller here: without `-u`, the registry answers 401. TLS protects the
credentials on the way.

## Step 4: Plain HTTP: redirected, or off

`registry` sets `QUARKUS_HTTP_INSECURE_REQUESTS: redirect`:

```bash
curl -si http://localhost:8080/apis/registry/v3/system/info | head -3
# HTTP/1.1 301 Moved Permanently
# location: https://localhost:8443/apis/registry/v3/system/info
```

The redirect names the container's HTTPS port, 8443, whatever port you published it on.

`registry-mtls` sets `disabled`: nothing listens on port 8080 at all.

```bash
docker compose exec registry-mtls curl -s http://localhost:8080/   # exit code 7: connection refused
```

The management interface (port 9000: `/health`, `/metrics`) serves HTTPS too, with the same
certificate, once a key store is set: health checks must use `https://` (see the Compose health
check). The operator switches its probes for you.

## Step 5: Mutual TLS

The registry image cannot require client certificates (see
[Mutual TLS](README.md#mutual-tls) in the README), so `mtls-proxy` does it
([`mtls-proxy/nginx.conf`](mtls-proxy/nginx.conf)):

```nginx
ssl_client_certificate  /certs/ca-cert.pem;
ssl_verify_client       on;
```

```bash
CA=(--cacert certs/ca-cert.pem)
curl "${CA[@]}" --cert certs/client-cert.pem --key certs/client-key.pem https://localhost:9443/apis/registry/v3/system/info   # 200
curl "${CA[@]}" https://localhost:9443/apis/registry/v3/system/info                                                         # 400
curl "${CA[@]}" --cert certs/rogue-client-cert.pem --key certs/rogue-client-key.pem https://localhost:9443/apis/registry/v3/system/info  # 400
```

| Client certificate | Answer |
|---|---|
| signed by the example CA | 200, from the registry |
| none | 400 `No required SSL certificate was sent` |
| signed by the rogue CA | 400 `The SSL certificate error` |

It is still mutual: without `--cacert`, curl refuses the proxy (exit code 60). The proxy checks the
registry's certificate too (`proxy_ssl_verify on`), and `registry-mtls` publishes no port.

## Step 6: The Maven plugin

[`clients/pom.xml`](clients/pom.xml) has one plugin execution per configuration; `demo.sh` runs each
by id:

```bash
../../mvnw -q -f clients io.apicurio:apicurio-registry-maven-plugin:3.4.0-SNAPSHOT:register@tls-pkcs12
```

```xml
<registryUrl>https://localhost:8443/apis/registry/v3</registryUrl>
<trustStorePath>${certs}/truststore.p12</trustStorePath>
<trustStorePassword>${store.password}</trustStorePassword>
<username>developer</username>
<password>developer</password>
```

| Execution | Configuration | Result |
|---|---|---|
| `tls-pkcs12` | PKCS12 trust store + username/password | registered |
| `tls-jks` | JKS trust store + username/password | registered |
| `tls-pem` | `ca-cert.pem`, `trustStoreType` `PEM` (no password) | registered |
| `dev-trust-all` | `trustAll` + `verifyHostname` `false`, by IP (development only) | registered |
| `mtls-pkcs12` | PKCS12 trust store + PKCS12 key store | registered through the proxy |
| `mtls-jks` | JKS trust store + JKS key store | registered through the proxy |
| `mtls-pem` | `ca-cert.pem` + `client-cert.pem` as `keyStorePath`, `client-key.pem` as `keyStorePemKeyPath` | registered through the proxy |
| `no-trust-store` | no trust store | refused: `PKIX path building failed` |
| `hostname-mismatch` | the right CA, by IP | refused: `No subject alternative names matching IP address 127.0.0.1` |
| `wrong-password` | TLS fine, wrong password | refused: 401 |
| `mtls-no-client-cert`, `mtls-rogue-client-cert` | no client certificate, or a rogue one | refused by the proxy (see below) |
| `download-mtls`, `download-tls` | the `download` goal, with the same options | the registered schema |

The store type comes from the file extension (`.p12`/`.pfx`, `.jks`, `.pem`/`.crt`/`.cer`) unless
`trustStoreType` or `keyStoreType` is set. The proxy's 400 is an HTML page, so the plugin reports it
as `Content type text/html does not have a factory to be parsed`, not as a 400.

OAuth2 combines with TLS the same way (`authServerUrl`, `clientId`, `clientSecret`, `clientScope`);
the [authentication example](../authentication/) runs it against Keycloak.

## Step 7: The Java SDK over TLS

[`Clients.java`](clients/src/main/java/io/apicurio/registry/examples/tls/Clients.java):

```java
RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create(url)
        .trustStorePem(certs + "/ca-cert.pem")       // or trustStoreJks / trustStorePkcs12(path, password)
        .basicAuth("developer", "developer"));
```

| Configuration | Result |
|---|---|
| PEM or JKS trust store + credentials | system info, groups |
| no trust store, or the rogue CA | the handshake fails (`SSLHandshakeException`) |
| wrong password | 401 |
| by IP | `No subject alternative names matching IP address 127.0.0.1 found` |
| by IP, `trustAll(true).verifyHost(false)` | accepted: development only |

## Step 8: The Java SDK over mutual TLS

```java
RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create(mtlsUrl)
        .keystorePkcs12(certs + "/client-keystore.p12", "apicurio")
        .trustStorePkcs12(certs + "/truststore.p12", "apicurio"));
```

The client creates an artifact, reads it back and deletes it. `keystorePem(cert, key)` and
`keystoreJks` work the same way. Without a key store, or with the rogue one, the proxy answers 400.

## Step 9: The Kafka serdes over mutual TLS

The serializer and deserializer call the registry through the proxy. The serializer uses PKCS12
stores:

```properties
apicurio.registry.url=https://localhost:9443/apis/registry/v3
apicurio.registry.tls.truststore.location=.../certs/truststore.p12
apicurio.registry.tls.truststore.password=apicurio
apicurio.registry.tls.truststore.type=PKCS12
apicurio.registry.tls.keystore.location=.../certs/client-keystore.p12
apicurio.registry.tls.keystore.password=apicurio
apicurio.registry.tls.keystore.type=PKCS12
```

and the deserializer PEM files:

```properties
apicurio.registry.tls.certificates=.../certs/ca-cert.pem
apicurio.registry.tls.client-certificate=.../certs/client-cert.pem
apicurio.registry.tls.client-key=.../certs/client-key.pem
```

The producer registers `tls-greetings-value` and sends a record; the consumer fetches the schema and
reads it back. (Kafka itself is plain here: these settings are only for the registry calls.)

## Clean up

```bash
docker compose down -v
rm certs/*.pem certs/*.p12 certs/*.jks     # optional: demo.sh generates new ones
```

## Presenter notes

- **Start before the talk:** run `./demo.sh` once beforehand; the first run builds the clients.
- **Show the refusals,** not only the successes: step 3's table and step 5's three curls are the
  heart of the demo. Each refusal happens before a single HTTP byte is exchanged, except the
  proxy's 400, which is an HTTP answer after the handshake.
- **Make the point about layers:** TLS proves the server's identity and encrypts; HTTP basic (or
  OAuth2) says who the caller is. Mutual TLS proves the caller holds a certificate from your CA.
- **Mention the redirect port:** the `location` header names 8443, the container's port.
- **Kubernetes:** point to [On Kubernetes](README.md#on-kubernetes): the operator's `spec.app.tls`
  sets the same two variables from a Secret.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `certs/ is empty but containers are running` | The certificates were deleted under running containers: `docker compose down -v`, then `./demo.sh` |
| curl exit code 60 with `--cacert` | The certificates were regenerated while the containers ran: `docker compose up -d --force-recreate` |
| curl exit code 35 or `Connection reset by peer` | The registry is still starting, or the request was plain HTTP to an HTTPS port |
| The registry logs `Cannot set quarkus.http.insecure-requests without enabling SSL` | No key store was picked up: check the paths, and for PEM files use a name, not `0`, in the variable |
| `Build time property cannot be changed at runtime: quarkus.http.ssl.client-auth` | Expected if you try `QUARKUS_HTTP_SSL_CLIENT_AUTH`: require client certificates in front of the registry |
| The serdes fail with `Path traversal detected` | The store path has a `..` element: normalize it |
| A health check over `http://localhost:9000` fails | With TLS, the management port serves HTTPS |
| `build.sh failed` | Needs JDK 21 or later; the log path is printed |

## Using other ports

Every host port is an environment variable, read by both Compose and `demo.sh`:

```bash
REGISTRY_PORT=18443 REGISTRY_HTTP_PORT=18080 MTLS_PORT=19443 KAFKA_PORT=19092 ./demo.sh
```

The commands in this guide then use those ports. The HTTP redirect still names 8443 (the container's
port): `demo.sh` checks the header, not the redirected call.
