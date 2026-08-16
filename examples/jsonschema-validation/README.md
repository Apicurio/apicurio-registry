# JSON Schema Validation Example

This example demonstrates client-side JSON validation using Apicurio Registry Schema Validation libraries.

It does the following:

- registers a JSON schema artifact in Registry
- validates a valid Java object
- validates an invalid Java object
- validates using dynamic artifact reference resolution

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
  -pl :apicurio-registry-examples-jsonschema-validation \
  -am \
  -DskipTests \
  -Dcheckstyle.skip=true \
  -Dspotbugs.skip=true \
  compile
```

## Run Standalone

```bash
mvn -f examples/jsonschema-validation/pom.xml \
  -Dexec.mainClass=io.apicurio.registry.examples.validation.json.JsonSchemaValidationExample \
  -Dexec.classpathScope=runtime \
  exec:java
```

## Expected Output

You should see:

- `Starting example JsonSchemaValidationExample`
- `Validating valid message bean`
- validation output indicating success
- `Validating invalid message bean`
- validation output indicating failure details
- `Validating message bean using dynamic ArtifactReference resolution`

## Artifact Created in Registry

The example creates/uses:

- Group: `default`
- Artifact ID: `JsonSchemaValidationExample`
- Artifact type: `JSON`

Verify:

```bash
curl -s http://localhost:8080/apis/registry/v3/groups/default/artifacts/JsonSchemaValidationExample | jq .
```

If `jq` is unavailable:

```bash
curl -s http://localhost:8080/apis/registry/v3/groups/default/artifacts/JsonSchemaValidationExample
```

## Optional OAuth2 Configuration

If your Registry requires OAuth2, set:

- `AUTH_TOKEN_ENDPOINT`
- `AUTH_CLIENT_ID`
- `AUTH_CLIENT_SECRET`

Example:

```bash
export AUTH_TOKEN_ENDPOINT="https://keycloak.example/realms/demo/protocol/openid-connect/token"
export AUTH_CLIENT_ID="registry-client"
export AUTH_CLIENT_SECRET="secret"
```

Then rerun the command in the Run section.

## Troubleshooting

- `Connection refused` / request errors:
  ensure Registry is running on `localhost:8080`.
- `401/403` errors:
  configure OAuth2 env vars for your secured registry.
- `ClassNotFoundException` at runtime:
  use `-Dexec.classpathScope=runtime` (not `compile`).
