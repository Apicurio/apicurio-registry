# Simple Validation Example

This example validates JSON messages against a JSON Schema fetched from Apicurio Registry, then publishes valid messages to a local mock broker.

## Prerequisites

- Java 17+ (`mvn -v`)
- Maven 3.9+
- Apicurio Registry running at `http://localhost:8080/apis/registry/v3`

Start a local registry (example):

```bash
docker run --rm -p 8080:8080 quay.io/apicurio/apicurio-registry:latest-snapshot
```

## Run Standalone

From the repository root:

```bash
# Build only modules required by this example
mvn -Pexamples \
  -pl :apicurio-registry-examples-simple-validation \
  -am \
  -DskipTests \
  -Dcheckstyle.skip=true \
  -Dspotbugs.skip=true \
  compile

# Run the example module
mvn -f examples/simple-validation/pom.xml \
  -Dexec.mainClass=io.apicurio.registry.examples.simple.json.SimpleValidationExample \
  -Dexec.classpathScope=runtime \
  exec:java
```

## Expected Output

You should see lines like:

- `Starting example SimpleValidationExample`
- `Schema registered successfully at Examples/MessageType`
- `Received message!`
- `Produced message: ...`
- `Done (success).`

## Verify Registry Artifact

```bash
curl -s http://localhost:8080/apis/registry/v3/groups/Examples/artifacts/MessageType | jq .
```

If `jq` is unavailable:

```bash
curl -s http://localhost:8080/apis/registry/v3/groups/Examples/artifacts/MessageType
```

## Configuration Overrides

The example supports environment overrides:

- `REGISTRY_URL` (default: `http://localhost:8080/apis/registry/v3`)
- `GROUP` (default: `Examples`)
- `ARTIFACT_ID` (default: `MessageType`)

Example:

```bash
REGISTRY_URL=http://localhost:8080/apis/registry/v3 \
GROUP=Examples \
ARTIFACT_ID=MessageType \
mvn -f examples/simple-validation/pom.xml \
  -Dexec.mainClass=io.apicurio.registry.examples.simple.json.SimpleValidationExample \
  -Dexec.classpathScope=runtime \
  exec:java
```

## Troubleshooting

- `ClassNotFoundException` for Kiota/OpenTelemetry classes:
  use `-Dexec.classpathScope=runtime` (not `compile`).
- `ArtifactNotFoundException` when checking `Examples/MessageType`:
  verify the run output includes `Schema registered successfully ...`.
- Connection errors to registry:
  ensure registry is running on port `8080` and `REGISTRY_URL` is correct.


`SimpleValidationExample` creates/registers an artifact in Registry:
- Group: `Examples`
- Artifact ID: `MessageType`
- Type: `JSON`

It uses `ifExists=FIND_OR_CREATE_VERSION`, so reruns won’t fail if it already exists; they may create/find versions as needed. It does not delete artifacts on exit.