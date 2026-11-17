# ADR-0008: Artifact References

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-10.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11029](https://github.com/Apicurio/apicurio-registry/issues/11029),
  [#11030](https://github.com/Apicurio/apicurio-registry/issues/11030),
  [#9626](https://github.com/Apicurio/apicurio-registry/issues/9626).
- **Scope:** How a schema refers to types defined in other artifacts: the reference model, storage
  and hashing, resolution (dereference, rewrite, graph), integrity, deletion, and the clients that
  create references (Maven plugin, serdes, server-side auto-references). Authorization of references
  is in [ADR-0005](0005-per-resource-authorization.md); integrity rules in general in
  [ADR-0006](0006-rules-engine.md).

## Reading this document

- Start with [the decision](#decision).
- For a running system: the [example](../examples/schema-references/), which checks every behaviour
  below.
- For code changes: the [source map](#source-map) and [verification](#example-and-verification).

## Context

Real schemas share types: an address, an amount, an enum of countries. Copying them into every schema
makes them drift; one artifact per shared type needs a way for a schema to say "my
`com.example.common.Money` is that artifact's version 1". Every format spells the dependency
differently (Avro type names, Protobuf imports, JSON `$ref`, XML `schemaLocation`), clients need both
the raw schema and a self-contained one, and a schema's meaning must not change when a dependency
evolves.

## Decision

1. **A reference maps a name to an exact artifact version:** `{name, groupId, artifactId, version}`.
   The name is the string the content uses (Avro full name, `$ref` value, imported file). References
   do not follow `latest`; pinning keeps a schema's meaning stable.
2. **References belong to the content.** They are stored with the content row, and **both hashes
   include them**: the same text with different references is different content, with its own
   content ID.
3. **Content is returned as stored by default.** On request, the registry inlines references
   (`DEREFERENCE`) or replaces them with registry URLs (`REWRITE`), where the type supports it.
4. **Finding references is per type** (`ReferenceFinder`), and used by clients (Maven plugin
   `autoRefs`, serdes) and by the `ALL_REFS_MAPPED` integrity level.
5. **Integrity is a rule, not built in:** `INTEGRITY` levels decide whether dangling, unmapped,
   duplicate or circular references are accepted.
6. **Readers must be able to read every reference** (any-of for shared content, all-of for
   resolution; ADR-0005).

### Why pin versions

A schema registered with "latest Money" would silently change when `Money` gets a new version, and
its content ID would no longer identify what consumers decode. Pinning makes evolution explicit: to
use a new `Money`, register a new version of the referrer.

### Why references are part of the hash

A content ID is shared by everything with the same content. If two versions had the same text but
different dependencies, sharing one content ID would make the ID ambiguous for deserializers.

## Model

### Storage

Table `content_references(contentId, groupId, artifactId, version, name)`, primary key
`(contentId, name)`, cascading with the content row; also serialized in `content.refs`. Two versions
with the same content share one reference list.

| Hash | Computed from |
|---|---|
| `contentHash` | SHA-256 of the bytes, plus the serialized references when there are any |
| `canonicalHash` | SHA-256 of the canonical form (per-type canonicalizer, with the resolved references), plus the references |

`GET /ids/contentHashes/{hash}` uses `contentHash`. `canonical=true` on create (with
`ifExists=FIND_OR_CREATE_VERSION`), search and content reads uses the canonical form; the
Confluent-compatible API uses it with `apicurio.ccompat.use-canonical-hash`.

### API

| Operation | Endpoint |
|---|---|
| Create with references | `content.references[]` in `POST /groups/{g}/artifacts` (first version) and `POST .../versions` |
| List outbound / inbound | `GET .../versions/{v}/references?refType=OUTBOUND|INBOUND`; `/ids/globalIds/{id}/references`; outbound only by content ID or hash |
| Graph | `GET .../versions/{v}/references/graph`: nodes, edges, depth, `hasCycles` |
| Read with references | `references=PRESERVE|DEREFERENCE|REWRITE` on `.../versions/{v}/content` and `/ids/globalIds/{id}`; default `PRESERVE` (`apicurio.rest.artifact.references.default-handling`) |
| Detect | `POST /content/references?artifactType=` |

| Type | DEREFERENCE | REWRITE |
|---|---|---|
| Avro, Protobuf | yes | 400 |
| OpenAPI, AsyncAPI, JSON Schema, OpenRPC, GraphQL, ODCS | yes | yes |
| XSD, WSDL | 400 | yes |
| Thrift, XML, Kafka Connect, Iceberg | content unchanged | content unchanged |

Resolution walks the graph breadth-first (`ReferenceGraphWalker`), up to
`apicurio.storage.references.max-depth` (default 100, then 409). Visited nodes are not expanded again.

### Integrity

`REFS_EXIST` (targets exist and have an allowed type), `ALL_REFS_MAPPED` (every reference in the
content has a mapping, per type), `NO_DUPLICATES`, `NO_CIRCULAR_REFERENCES`, `FULL`. Without a rule,
a reference may point at a version that does not exist yet.

### Lifecycle of targets

| Target | Effect on referrers |
|---|---|
| `DISABLED` | Still resolves |
| Deleted (core API) | Allowed. The reference stays listed; resolution skips it or fails (Avro: 500) ([#11029](https://github.com/Apicurio/apicurio-registry/issues/11029), [#9626](https://github.com/Apicurio/apicurio-registry/issues/9626)) |
| Deleted (Confluent-compatible API) | Refused with 422 `REFERENCE_EXISTS` while referenced |
| Content no longer used by any version | Removed by `OrphanedContentReaper` (`apicurio.storage.orphan-cleanup.every`, default `1h`) |

### Clients that create references

- **Maven plugin `register`:** nested `<references>` (each one an artifact to register, with its own
  references), `existingReferences`, or `autoRefs` with naming strategies (`autoRefsNamingStrategy`,
  `avroAutoRefsNamingStrategy`, default: the Avro namespace as group). Leaves are registered first.
- **Serdes with `auto-register`:** Avro named types defined inline and Protobuf imports are
  registered first, each as its own artifact; JSON Schema cannot derive them from data.
  `apicurio.registry.dereference-schema` registers and reads inlined schemas instead.
- **Server-side auto-references** (`CreateVersion.autoReferences`): the registry matches names to
  existing artifacts, choosing the latest enabled version at write time and storing it.

### Behaviour across storage variants

References are stored and resolved through the common SQL layer, so SQL and KafkaSQL behave the
same. GitOps and KubernetesOps load references from their source files; they are read-only through
the API.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.rest.artifact.references.default-handling` | `PRESERVE` | Default `references` mode |
| `apicurio.storage.references.max-depth` | `100` | Maximum resolution depth |
| `apicurio.apis.v3.base-href` | `_` (from the request) | Base URL for `REWRITE` |
| `apicurio.storage.orphan-cleanup.every` | `1h` | Orphaned content removal |
| `apicurio.registry.dereference-schema` (client) | `false` | Serdes register/read inlined schemas |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Resolution | [`references`](../app/src/main/java/io/apicurio/registry/references/) | `ReferenceGraphWalker`, `ReferenceResolution`, `AuthorizingReferenceLoader`, auto-references |
| Hashing, content | [`RegistryContentUtils`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/RegistryContentUtils.java) | Hashes, canonicalization, dereference with context |
| Per type | [`schema-util`](../schema-util/) | `ReferenceFinder`, `ContentDereferencer`, canonicalizers, validators |
| Maven plugin | [`utils/maven-plugin`](../utils/maven-plugin/) | `register` with references and `autoRefs` |
| Serdes | [`schema-resolver`](../schema-resolver/) | Registering nested schemas, resolving references on read |
| Cleanup | [`OrphanedContentReaper`](../app/src/main/java/io/apicurio/registry/storage/OrphanedContentReaper.java) | Orphaned content |

## Limitations and open work

1. Deleting a referenced artifact is not prevented in the core API, and breaks its referrers
   ([#11029](https://github.com/Apicurio/apicurio-registry/issues/11029)).
2. A failed reference load is skipped rather than reported, and two targets with the same name
   collapse into one ([#9626](https://github.com/Apicurio/apicurio-registry/issues/9626), #9000).
3. `NO_CIRCULAR_REFERENCES` misses a cycle closed by the version being created, and dereferencing a
   cycle fails with 500 ([#11030](https://github.com/Apicurio/apicurio-registry/issues/11030)).
4. The legacy SQL resolver truncates at the depth limit with only a warning (#10635).
5. References cannot follow `latest`; moving a dependency needs a new version of every referrer.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| References to `latest` | Not selected: a schema's meaning, and a content ID, would change under consumers |
| References per version rather than per content | Not selected: the content ID must identify text and dependencies together |
| Always dereference on write | Not selected: loses the shared types, and their evolution |
| Block deletion of referenced artifacts | Open: done in the Confluent-compatible API only ([#11029](https://github.com/Apicurio/apicurio-registry/issues/11029)) |

## Consequences

- **Benefits:** shared types are registered once and evolve explicitly; one model for every format,
  including references across types; clients can choose raw, inlined or linked schemas.
- **Costs:** clients must register dependencies first and pin versions; per-type support differs;
  deletion and cycles have the gaps above.

## Example and verification

- **Example:** [`examples/schema-references`](../examples/schema-references/):
  `docker compose up -d && ./demo.sh` registers Avro, JSON Schema and AsyncAPI schemas with the Maven
  plugin (manual and automatic references), checks listing, graph, the three read modes, hashing,
  canonical matching, integrity, disabled and deleted targets, and Kafka serializers registering
  nested Avro types and Protobuf imports. CI runs it against each pull request.
- **User documentation:**
  [`assembly-artifact-reference.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-artifact-reference.adoc).

| Concern | Tests to read |
|---|---|
| Dereference and rewrite | `ServerSideDereferenceTest`, `ServerSideDereferenceDefaultTest` (app) |
| Graph, depth, batching | `ReferenceGraphWalkerTest`, `ReferenceResolutionTest`, `ReferenceDepthLimitTest`, `BatchReferenceResolutionTest` (app) |
| Integrity | `CircularReferenceIntegrityRuleTest`, `DuplicateReferencesTest` (app) |
| Auto-references | `AutoReferencesTest`, `ReferenceMatcherTest` (app) |
| Maven plugin | `RegistryMojoWithReferencesTest`, `RegistryMojoWithAutoReferencesTest` (app) |
| Per type | `*ReferenceFinderTest`, `*DereferencerTest` (schema-util) |
| Hashing | `ContentCanonicalizerTest`, `AvroCanonicalHashUpgraderTest` (app) |

```bash
./mvnw test -pl app -Dtest='ServerSideDereferenceTest,ReferenceGraphWalkerTest,ReferenceDepthLimitTest'
```

## History

| Milestone | Result |
|---|---|
| 3.4.0 | Server-side auto-references; breadth-first resolution with a depth limit; reference authorization batching (ADR-0005) |
| This ADR | First written record, with a verified example |
