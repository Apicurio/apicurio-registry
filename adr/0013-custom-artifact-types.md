# ADR-0013: Custom and Configured Artifact Types

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-15.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#6086](https://github.com/Apicurio/apicurio-registry/pull/6086),
  [#7910](https://github.com/Apicurio/apicurio-registry/pull/7910),
  [#11151](https://github.com/Apicurio/apicurio-registry/issues/11151),
  [#11152](https://github.com/Apicurio/apicurio-registry/issues/11152) (fixed).
- **Scope:** How the set of artifact types is configured, and how a type's behaviour (detection,
  validation, compatibility, canonicalization, references, dereferencing) is provided: Java classes
  or webhooks. Not covered: the rules engine that calls the providers
  ([ADR-0006](0006-rules-engine.md)) and the built-in types themselves.

## Context

Teams register more than Avro, Protobuf and OpenAPI: internal DSLs, documentation, data formats.
Each type needs the same behaviour as a built-in one, so that rules, de-duplication and references
work: is this content of this type, is it valid, is a new version compatible, what is its canonical
form, what does it reference. The registry ships as a container image built with Quarkus, so it
cannot load arbitrary jars at runtime, and many users cannot or do not want to build Java code.

## Decision

1. **The set of types is a configuration file** (`apicurio.artifact-types.config-file`): a JSON list
   of types, each with its name, content types, file extensions and one **provider per operation**.
   `includeStandardArtifactTypes` keeps the built-in types. Without the file, only the built-in types
   exist.
2. **A type is the same per-operation SPI as a built-in one:** `ContentAccepter`, `ContentValidator`,
   `CompatibilityChecker`, `ContentCanonicalizer`, `ContentDereferencer` and `ReferenceFinder` from
   `apicurio-registry-schema-util-common`. A missing provider means "skip": no detection, always
   valid, always compatible, no canonical form, no references.
3. **Two kinds of provider:**
   - **`java`**: a class implementing the interface, loaded from the class path. Because the image
     is a Quarkus fast-jar, classes are added with the **`-mutable` image**: copy the jar into
     `/deployments/quarkus-app/providers` and run `/deployments/build.sh` (re-augmentation) in a
     derived image.
   - **`webhook`**: an HTTP endpoint the registry POSTs a JSON request to, one per operation, with
     the contract in `common/src/main/resources/META-INF/artifact-type-webhooks.json`. It works with
     the standard image and in any language.
4. **No script providers.** A JavaScript (QuickJS) provider existed from 3.1 and was removed in
   [#7910](https://github.com/Apicurio/apicurio-registry/pull/7910), once the types that needed it
   (MODEL_SCHEMA, PROMPT_TEMPLATE) became native Java types. Running user scripts inside the
   registry needs a sandbox, a runtime dependency and a library of built-ins to maintain; Java
   classes and webhooks cover the same needs.
5. **Webhook URLs are operator configuration,** read from a file on the server, so they are trusted
   like any other configuration and are not checked by the outbound address policy
   (`OutboundAddressPolicy`) that guards URLs supplied through the API (federation peers, MCP
   imports).

### Why Java classes need a derived image

Quarkus resolves the class path at build time (augmentation). A mutable-jar can be re-augmented with
extra jars, which costs one build step in a derived image (seconds, about 1 GB of memory) and keeps
the runtime and startup time unchanged. Loading jars at runtime would need a custom class loader in
every code path that instantiates providers.

### Why webhooks

A webhook needs no image and no Java, and can be shared by several registries. The cost is a network
round trip for every operation, and a dependency: rules depend on the webhook being up.

## Model

### Configuration file

```json
{
  "includeStandardArtifactTypes": true,
  "artifactTypes": [{
    "artifactType": "CSV", "name": "CSV header", "contentTypes": ["text/csv"], "fileExtensions": [".csv"],
    "contentAccepter":      {"type": "webhook", "url": "http://csv-webhook:8080/accept", "headers": {}},
    "contentValidator":     {"type": "java", "classname": "com.example.CsvValidator"},
    "compatibilityChecker": {"type": "webhook", "url": "http://csv-webhook:8080/compatibility"}
  }]
}
```

Providers can be mixed within a type.

### What each provider does

| Provider | Called when | Without it |
|---|---|---|
| `contentAccepter` | An artifact is created without `artifactType`: each type is asked in turn | The type is never detected |
| `contentValidator` | The `VALIDITY` rule (and `INTEGRITY` for references) | All content is valid |
| `compatibilityChecker` | The `COMPATIBILITY` rule | All content is compatible |
| `contentCanonicalizer` | Canonical hashes: `canonical=true` lookups, de-duplication | Canonical form = content |
| `referenceFinder` | Finding external references (for example, by the Maven plugin) | No references found |
| `contentDereferencer` | `references=DEREFERENCE` or `REWRITE` on retrieval | Content returned as is |

### Failures

| Situation | Behaviour |
|---|---|
| Configuration file malformed | Logged; the registry starts with the built-in types only |
| Java class not found or not instantiable | Providers are created when an operation needs them, so the registry starts, and the first request that uses the provider fails |
| Webhook unreachable or failing | Fails closed; the cause is logged, not sent to the client. The validator rejects the change (`VALIDITY` violation); the compatibility checker reports an incompatibility with a generic message; the canonicalizer, reference finder and dereferencer fail the request with 502 (`ArtifactTypeWebhookException`); the accepter does not accept the content |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.artifact-types.config-file` | `/tmp/apicurio-registry-artifact-types.json` | The artifact types configuration |

| `apicurio.artifact-types.webhooks.connect-timeout.seconds` | `5` | Connect timeout of a webhook call |
| `apicurio.artifact-types.webhooks.read-timeout.seconds` | `10` | Read timeout of a webhook call |

Webhook calls use their own web client (`HttpClientService`, `WebClientProducer`). Failed calls are
retried, up to 10 seconds per call (`@Timeout`), so an unreachable webhook fails a request after
about 10 seconds.

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Configuration model | [`config/artifactTypes`](../common/src/main/java/io/apicurio/registry/config/artifactTypes/) | Types, Java and webhook providers |
| Loading | [`ArtifactTypeUtilProviderImpl`](../app/src/main/java/io/apicurio/registry/types/provider/configured/ArtifactTypeUtilProviderImpl.java) | Reads the file, merges the built-in types |
| Delegation | [`types/provider/configured`](../app/src/main/java/io/apicurio/registry/types/provider/configured/) | One `Configured*` class per operation, with Java and webhook delegates |
| Webhook contract | [`artifact-type-webhooks.json`](../common/src/main/resources/META-INF/artifact-type-webhooks.json) | Request and response formats |
| SPI | `schema-util/common` | The provider interfaces |
| Mutable image | [`Dockerfile.mutable.jvm`, `build.sh`](../distro/docker/src/main/docker/) | Re-augmentation with `providers/` |

## Limitations and open work

1. Webhook `headers` are not sent, and the ContentAccepter webhook reads a bare boolean instead of
   the contract's `{"accepted": ...}` ([#11151](https://github.com/Apicurio/apicurio-registry/issues/11151)).
2. A malformed configuration file is only logged, and the custom types are silently missing.
3. A provider jar must be compiled against the same registry minor version, and the derived image
   rebuilt for each registry upgrade.
4. Custom types have no UI-specific support (icon, editor).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Script (JavaScript) providers | Removed in #7910: a sandboxed runtime and built-ins to maintain, no longer needed once the LLM types became native |
| Loading jars at runtime | Not selected: a custom class loader everywhere providers are created; re-augmentation reuses Quarkus |
| Webhooks only | Not selected: a network round trip per operation, and an extra service to run |
| Types defined by the API rather than a file | Not selected: types change behaviour for every artifact; they are operator configuration |

## Consequences

- **Benefits:** any format can get rules, de-duplication and references; no fork of the registry;
  webhooks in any language.
- **Costs:** a derived image for Java providers; a runtime dependency for webhooks; the failure
  behaviour is inconsistent today.

## Example and verification

- **Example:** [`examples/custom-artifact-types`](../examples/custom-artifact-types/): `./demo.sh`
  builds a derived image with Java providers for `MARKDOWN` and runs a webhook service for `CSV`,
  then checks detection, `VALIDITY`, `COMPATIBILITY` and canonical lookups for both, which webhook
  URLs are called, and what happens when the webhook is down. CI runs it on this commit's `-mutable`
  image.
- **User documentation:**
  [`assembly-custom-artifact-types.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-custom-artifact-types.adoc).

| Concern | Tests to read |
|---|---|
| Java providers | `JavaClassArtifactTypesTest` (app) |
| Webhook providers | `WebhookArtifactTypesTest` (app) |
| Re-augmentation of the mutable image | the "Smoke Test Re-augmentation" step of `verify.yaml` |

## History

| Milestone | Result |
|---|---|
| 3.1 ([#6086](https://github.com/Apicurio/apicurio-registry/pull/6086)) | Configured artifact types: Java, webhook and script providers |
| [#7910](https://github.com/Apicurio/apicurio-registry/pull/7910) | Script providers removed |
| This ADR | First written record, with a verified example |
| [#11176](https://github.com/Apicurio/apicurio-registry/pull/11176) | Fixed: an unreachable webhook fails closed with a generic message after about 10 seconds, instead of letting content through `VALIDITY` or exposing exception text; webhook connect and read timeouts are configurable (#11152) |
