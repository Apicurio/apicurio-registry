# Rest Client Example

This example demonstrates how to use the Apicurio Registry Java SDK to:

- create artifacts
- fetch artifacts
- (optionally) load many artifacts for stress/load testing

## Prerequisites

- Java 17+
- Maven 3.9+
- Apicurio Registry running at `http://localhost:8080/apis/registry/v3`

Start a local registry (example):

```bash
docker run --rm -p 8080:8080 quay.io/apicurio/apicurio-registry:latest-snapshot
```

## Build Standalone (from repo root)

```bash
mvn -Pexamples \
  -pl :apicurio-registry-examples-rest-client \
  -am \
  -DskipTests \
  -Dcheckstyle.skip=true \
  -Dspotbugs.skip=true \
  compile
```

## Run Standalone

Use `runtime` classpath scope so Maven includes Kiota transitive runtime dependencies.
`compile` scope is not enough for this example's execution path.

Key runtime dependencies pulled transitively from the Kiota stack include:

- `io.opentelemetry:opentelemetry-api` (for `io.opentelemetry.api.GlobalOpenTelemetry`)
- `io.github.std-uritemplate:std-uritemplate` (for `io.github.stduritemplate.StdUriTemplate`)

If `runtime` scope is not used, you can see errors like:

- `NoClassDefFoundError: io/opentelemetry/api/GlobalOpenTelemetry`
- `NoClassDefFoundError: io/github/stduritemplate/StdUriTemplate`

### 1) Basic Demo (create + fetch in group `default`)

```bash
mvn -f examples/rest-client/pom.xml \
  -Dexec.mainClass=io.apicurio.registry.examples.SimpleRegistryDemo \
  -Dexec.classpathScope=runtime \
  exec:java
```

### 2) Basic Auth Variant (same flow, different demo class)

```bash
mvn -f examples/rest-client/pom.xml \
  -Dexec.mainClass=io.apicurio.registry.examples.SimpleRegistryDemoBasicAuth \
  -Dexec.classpathScope=runtime \
  exec:java
```

### 3) Registry Loader (load/stress scenario)

This creates many artifacts and rules in group `default`.

```bash
mvn -f examples/rest-client/pom.xml \
  -Dexec.mainClass=io.apicurio.registry.examples.RegistryLoader \
  -Dexec.classpathScope=runtime \
  exec:java
```

## OAuth2 Environment Variables (optional)

If your registry requires OAuth2, set:

- `AUTH_TOKEN_ENDPOINT`
- `AUTH_CLIENT_ID`
- `AUTH_CLIENT_SECRET`

Example:

```bash
export AUTH_TOKEN_ENDPOINT="https://keycloak.example/realms/demo/protocol/openid-connect/token"
export AUTH_CLIENT_ID="registry-client"
export AUTH_CLIENT_SECRET="secret"
```

Then run one of the demo commands above.

## Verify Artifacts

List artifacts in the default group:

```bash
curl -s http://localhost:8080/apis/registry/v3/groups/default/artifacts | jq .
```

If `jq` is unavailable:

```bash
curl -s http://localhost:8080/apis/registry/v3/groups/default/artifacts
```

## Cleanup

Delete all artifacts in group `default`:

```bash
curl -s http://localhost:8080/apis/registry/v3/groups/default/artifacts | \
  jq -r '.[].id' | \
  xargs -I{} curl -s -X DELETE "http://localhost:8080/apis/registry/v3/groups/default/artifacts/{}"
```

## Troubleshooting

- `ClassNotFoundException` for Kiota/OpenTelemetry classes:
  use `-Dexec.classpathScope=runtime` (not `compile`).
- `Connection refused` to registry:
  make sure Registry is running on `localhost:8080`.
- `401/403` from registry:
  configure OAuth2 env vars (or use a non-auth local registry).
