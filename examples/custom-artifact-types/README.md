# Custom Artifact Types (Java providers)

This example adds a custom **`MARKDOWN`** artifact type to Apicurio Registry, implemented in
Java and loaded into the registry container image **without rebuilding the registry**.

Since Apicurio Registry 3.1 the set of artifact types can be configured with a JSON file
(`apicurio.artifact-types.config-file`). Each type's behaviour (content acceptance, validation,
compatibility checking, canonicalization, ...) is delegated either to a **webhook** or to a
**Java class**. Java classes must be on the registry's class path, and because the registry is a
Quarkus *fast-jar*, a jar cannot simply be copied into the image. The `-mutable` image variant
solves this: it is a Quarkus *mutable-jar* that can be re-augmented with the jars found in
`/deployments/quarkus-app/providers` by running `/deployments/build.sh` (the same mechanism
Keycloak uses for its custom providers).

## What is in here

| File | Purpose |
|---|---|
| `src/main/java/.../Markdown*.java` | The providers: `MarkdownContentAccepter` (auto-detection), `MarkdownContentValidator` (`VALIDITY` rule), `MarkdownCompatibilityChecker` (`COMPATIBILITY` rule), `MarkdownContentCanonicalizer` (canonical content for de-duplication/search) |
| `artifact-types.json` | Registers the `MARKDOWN` type and points at the classes above (`"type": "java"`) |
| `Dockerfile` | `FROM apicurio/apicurio-registry:VERSION-mutable`, copies the jar into `providers/`, runs `build.sh --prune` |
| `docker-compose.yml` | Registry (built from the Dockerfile) and the registry UI |
| `samples/*.md` | Documents used by the walkthrough below |
| `test.sh` | Runs the whole walkthrough end to end |

The type itself is deliberately simple: a document must start with a level-1 heading (its
title), level-2 headings are its sections, a new version is backward compatible when it keeps
the title and all existing sections.

## Quick start

```bash
./test.sh
```

or step by step:

```bash
mvn package                       # builds target/custom-artifact-types.jar (no dependencies)
docker compose up --build         # builds the derived image and starts registry + UI
```

Use `REGISTRY_IMAGE=apicurio/apicurio-registry:3.4.0-mutable docker compose up --build` to pick a
specific registry version (the default is `latest-snapshot-mutable`). The provider jar must be
compiled against the same registry minor version (`apicurio-registry-schema-util-common`).

## Walkthrough

```bash
API=http://localhost:8080/apis/registry/v3

# MARKDOWN is now one of the artifact types
curl -s $API/admin/config/artifactTypes | jq 'map(.name)'

# Create an artifact WITHOUT specifying the type: the ContentAccepter detects MARKDOWN
jq -n --rawfile c samples/getting-started.md \
  '{artifactId:"orders-getting-started", firstVersion:{content:{content:$c, contentType:"text/markdown"}}}' \
  | curl -s -X POST -H 'Content-Type: application/json' -d @- $API/groups/docs/artifacts | jq .artifact.artifactType

# Enable validation on the artifact: a document without a title is rejected (HTTP 409)
curl -s -X POST -H 'Content-Type: application/json' -d '{"ruleType":"VALIDITY","config":"FULL"}' \
  $API/groups/docs/artifacts/orders-getting-started/rules
jq -n --rawfile c samples/invalid-no-title.md '{content:{content:$c, contentType:"text/markdown"}}' \
  | curl -s -X POST -H 'Content-Type: application/json' -d @- $API/groups/docs/artifacts/orders-getting-started/versions | jq .

# Enable backward compatibility: removing a section is rejected (HTTP 400)
curl -s -X POST -H 'Content-Type: application/json' -d '{"ruleType":"COMPATIBILITY","config":"BACKWARD"}' \
  $API/groups/docs/artifacts/orders-getting-started/rules
jq -n --rawfile c samples/getting-started-v2-removed-section.md '{content:{content:$c, contentType:"text/markdown"}}' \
  | curl -s -X POST -H 'Content-Type: application/json' -d @- $API/groups/docs/artifacts/orders-getting-started/versions | jq .

# Accepted: a section was added -> version 2
jq -n --rawfile c samples/getting-started-v2.md '{content:{content:$c, contentType:"text/markdown"}}' \
  | curl -s -X POST -H 'Content-Type: application/json' -d @- $API/groups/docs/artifacts/orders-getting-started/versions | jq .version
```

The UI at http://localhost:8888 shows the artifact with type `MARKDOWN` (custom types have no
dedicated icon).

## How the image is built

```dockerfile
FROM apicurio/apicurio-registry:VERSION-mutable
COPY --chown=1001:0 target/custom-artifact-types.jar /deployments/quarkus-app/providers/
COPY --chown=1001:0 artifact-types.json /deployments/artifact-types.json
RUN /deployments/build.sh --prune
ENV APICURIO_ARTIFACT_TYPES_CONFIG_FILE=/deployments/artifact-types.json
```

`build.sh` runs a Quarkus re-augmentation (`java -Dquarkus.launch.rebuild=true -jar quarkus-run.jar`)
on a scratch copy of the application and copies back only the regenerated class-path index, so the
derived image is only a few MB larger than the base image. It takes about 10 seconds and needs
roughly 1 GB of memory at image build time, and must be re-run whenever the jars in `providers/`
change. Runtime behaviour and startup time of the registry are unchanged. `--prune` removes
`lib/deployment` (the Quarkus deployment jars, about 47 MB, which the `-mutable` image carries in
addition to the standard image) from the file system of the derived image; the image can then
not be re-augmented again.

## Alternative: webhook providers

If you prefer not to build a derived image, the same `artifact-types.json` can point at an HTTP
service instead of Java classes, and the standard registry image can be used:

```json
"contentValidator": { "type": "webhook", "url": "http://markdown-service:8080/validate", "headers": {} }
```

The registry then POSTs the content to your service for each operation. See the "Custom artifact
types" chapter of the documentation for the request/response payloads.
