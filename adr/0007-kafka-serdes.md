# ADR-0007: Kafka Serdes and Schema Resolver

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-09.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11018](https://github.com/Apicurio/apicurio-registry/issues/11018),
  [#11005](https://github.com/Apicurio/apicurio-registry/issues/11005).
- **Scope:** The client libraries that let Kafka producers and consumers use schemas from the
  registry: the Avro, JSON Schema and Protobuf serializers and deserializers, the schema resolver they
  share, the wire format, naming strategies, caching, and the Kafka Connect converters. Pulsar and
  NATS serdes reuse the same resolver and are only listed. Not covered: the Confluent-compatible API
  ([ADR-0001](0001-confluent-schema-registry-compatibility.md)) and the rules the registry applies
  ([ADR-0006](0006-rules-engine.md)).

## Reading this document

- Start with [the decision](#decision).
- For client configuration: [configuration](#configuration) and the
  [user documentation](../docs/modules/ROOT/pages/getting-started/assembly-configuring-kafka-client-serdes.adoc).
- For a running system: the [example](../examples/kafka-serdes/), which checks every behaviour below.
- For code changes: the [source map](#source-map) and [verification](#example-and-verification).

## Context

A Kafka record is opaque bytes. To decode it, a consumer needs the exact schema the producer wrote
with, and producers and consumers evolve independently. Sending the schema with every record is too
expensive; agreeing out of band does not survive evolution. The registry is the shared place for
schemas, so the clients need a compact, stable way to point at a schema, a policy for registering and
finding schemas, and caching so that the registry is not on every record's path. Kafka users also
expect interoperability with Confluent's clients, and Kafka Connect needs the same behaviour as a
converter.

## Decision

1. **A record points at its schema by ID, not by name:** by default a magic byte `0x0` followed by the
   4-byte **content ID** (the ID of the schema content, shared by identical schemas), then the
   serialized data. Optionally the ID travels in Kafka headers instead.
2. **One resolver for every format and transport:** `DefaultSchemaResolver` in `schema-resolver` maps
   a record to an artifact (strategy), registers or finds the schema, and caches it. The format serdes
   only parse and serialize.
3. **Naming is a pluggable strategy;** the default is the topic: `default/<topic>-value` (or `-key`).
4. **Registration is opt-in:** `auto-register` is off by default, so production clients only use
   schemas registered by someone else (a build, an administrator) unless configured otherwise.
5. **The registry enforces rules, not the client:** an auto-registered schema goes through the same
   rules as any other write, and a rejection fails `send()`.
6. **Lookups are cached.** The deserializer's lookups by ID never expire. The resolver's entries,
   which the serializer uses, expire after `check-period-ms` (30 s): after that, a refresh needs the
   registry, unless `fault-tolerant-refresh` keeps serving the stale entry.
7. **Kafka Connect uses the same serdes** through converters.

### Why the content ID by default

Identical schemas registered under different artifacts share one content ID, as Confluent's schema
IDs do; that makes the framing identical to Confluent's for Avro and JSON Schema, through the
registry's Confluent-compatible API, which maps the Confluent ID to the content ID. The global ID
(unique per version) remains available with `apicurio.registry.use-id=globalId`.

### Why registration is off by default

A client that registers whatever it sends turns every deployment into a schema change. Making it
explicit leaves the choice to the team: registering from the build (Maven plugin), from an
administrator, or, for development, from the producer.

## Model

### Wire format

| Mode | Payload | Headers |
|---|---|---|
| Default | `0x0`, ID (4 bytes, content ID), data | none |
| `apicurio.registry.id-handler=...Legacy8ByteIdHandler` | `0x0`, ID (8 bytes), data | none |
| `apicurio.registry.headers.enabled=true` | data only | `apicurio.value.contentId` (or `.globalId`; `.key.` for keys), plus per format: Avro `apicurio.value.encoding`, JSON Schema and Protobuf `apicurio.value.msgType`, Protobuf `apicurio.protobuf.type` |

The deserializer reads headers only when headers are enabled on it, and otherwise expects the magic
byte (`IllegalStateException("Unknown magic byte!")`). `OptimisticFallbackIdHandler` reads both the 4-
and 8-byte forms. Protobuf adds a type reference by default, which Confluent's deserializer does not
expect; `send-type-ref=false` with `send-indexes=true` (and the reverse on reading) gives Confluent's
layout.

### Finding the schema (serializer)

[`DefaultSchemaResolver.resolveSchema`](../schema-resolver/src/main/java/io/apicurio/registry/resolver/DefaultSchemaResolver.java):

1. The strategy, then any explicit `apicurio.registry.artifact.group-id` / `.artifact-id` /
   `.version`, give the artifact reference; the cache is checked.
2. With `auto-register=true`, the schema (derived from the data for Avro and Protobuf; for JSON Schema read from
   `apicurio.registry.artifact.schema.location`, or taken from the record's headers with
   `use-schema-from-headers`) is created with
   `ifExists=FIND_OR_CREATE_VERSION` (`apicurio.registry.auto-register.if-exists`), references first.
3. Otherwise, with `find-latest=true` or an explicit version, the version is looked up by coordinates.
4. Otherwise the schema derived from the data is searched by content in the artifact; JSON Schema,
   which cannot derive one, uses the latest version.

| Strategy | Group | Artifact |
|---|---|---|
| `TopicIdStrategy` (default) | `default` | `<topic>-value` / `-key` |
| `SimpleTopicIdStrategy` | `default` | `<topic>` |
| `RecordIdStrategy` (Avro) | record namespace | record name |
| `TopicRecordIdStrategy` (Avro) | record namespace | `<topic>-<name>` |
| `QualifiedRecordIdStrategy` (Avro) | `default` | record full name |
| Custom `ArtifactReferenceResolverStrategy` | any | any |

### Reading (deserializer)

[`AbstractDeserializer`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/AbstractDeserializer.java)
reads the ID, resolves the schema by content ID (or global ID) with its references, and caches it.
Avro returns `GenericRecord` unless `use-specific-avro-reader` or a reflect datum provider is set;
Protobuf returns `DynamicMessage` unless a return class or `apicurio.protobuf.derive.class` is set;
JSON Schema returns a `JsonNode` unless a return class is set. An optional fallback artifact
(`apicurio.registry.fallback.*`) is used when resolution fails.

### Failures

| Situation | What the client sees |
|---|---|
| Rule violation on auto-register | `send()` throws `RuntimeException` caused by the SDK's `RuleViolationProblemDetails` (no message of its own, [#11018](https://github.com/Apicurio/apicurio-registry/issues/11018)) |
| JSON Schema data invalid (`validation-enabled`, default `true`) | `send()` throws an `UncheckedIOException` naming the failing field |
| No schema found by content | `RuntimeException("Could not resolve artifact reference by content ...")` |
| Unknown first byte | `IllegalStateException("Unknown magic byte!")` |
| Registry unreachable | Consumers keep decoding IDs they have seen. Producers fail once their entries expire (`check-period-ms`) and the retries are exhausted, unless `apicurio.registry.fault-tolerant-refresh=true`, which keeps serving the stale entry |

### Behaviour across storage variants

The serdes only use the REST API, so they behave the same on every storage variant. On GitOps and
KubernetesOps, which are read-only through the API, `auto-register` cannot create artifacts; schemas
must come from the source data.

## Configuration

Main properties (prefix `apicurio.registry.`); the configuration docs list them all.

| Property | Default | Meaning |
|---|---|---|
| `url` | none | Registry v3 API URL |
| `auto-register` | `false` | Register the schema when not found |
| `auto-register.if-exists` | `FIND_OR_CREATE_VERSION` | Behaviour when the artifact exists |
| `find-latest` | `false` | Use the latest version by coordinates instead of a content search |
| `artifact-resolver-strategy` | `TopicIdStrategy` | Naming |
| `artifact.group-id`, `artifact.artifact-id`, `artifact.version` | none | Explicit coordinates |
| `artifact.schema.location` | none | Classpath schema for JSON Schema |
| `use-id` | `contentId` | ID written in the record |
| `id-handler` | `Default4ByteIdHandler` | 4- or 8-byte IDs |
| `headers.enabled` | `false` | IDs in headers instead of the payload |
| `check-period-ms` | `30000` | Cache expiry for lookups by coordinates |
| `schema-resolver` | `DefaultSchemaResolver` | Custom resolver |
| `avro-datum-provider` | `DefaultAvroDatumProvider` | `ReflectAvroDatumProvider` for plain Java classes |
| `serde.validation-enabled` | `true` | Validate JSON Schema / Protobuf data |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Resolver | [`schema-resolver`](../schema-resolver/src/main/java/io/apicurio/registry/resolver/) | Strategies, lookup, registration, cache, client facades (v3, v2) |
| Serde core | [`serde-common`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/) | Magic byte, ID handlers, `SerdeConfig`, topic strategies |
| Formats | [`serdes/generic`](../serdes/generic/) | Avro, JSON Schema, Protobuf serializers and record strategies |
| Kafka | [`serdes/kafka`](../serdes/kafka/) | `KafkaSerializer`/`KafkaDeserializer`, headers handler, per-format Kafka classes |
| Connect | [`utils/converter`](../utils/converter/src/main/java/io/apicurio/registry/utils/converter/) | `AvroConverter`, `ProtobufConverter`, `ExtJsonConverter`, `SerdeBasedConverter` |
| Other transports | [`serdes/pulsar`](../serdes/pulsar/), [`serdes/nats`](../serdes/nats/) | Avro serdes for Pulsar and NATS |

## Limitations and open work

1. A rule violation reaches the producer without a message
   ([#11018](https://github.com/Apicurio/apicurio-registry/issues/11018)).
2. Protobuf lookups by content can fail, because the schema derived from a class is not the registered
   `.proto`; use `find-latest` or explicit coordinates (the validation library has the same issue,
   [#11005](https://github.com/Apicurio/apicurio-registry/issues/11005)).
3. Headers mode must be enabled on the consumer as well; a consumer without it fails with an
   unhelpful error.
4. Protobuf framing is not Confluent-compatible without four extra settings.
5. JSON Schema cannot derive a schema from data: auto-registration needs a schema location, or a schema in Kafka headers (`headers.enabled` with `use-schema-from-headers`).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Send the schema with each record | Not selected: size and parsing cost on every record |
| Global ID by default | Kept as an option: different IDs for identical schemas break Confluent expectations |
| Auto-register by default | Not selected: every deployment would become a schema change |
| Client-side compatibility checks | Not selected: one enforcement point, the registry ([ADR-0006](0006-rules-engine.md)) |
| Headers by default | Not selected: not Confluent-compatible, and headers are dropped by some tools |

## Consequences

- **Benefits:** 5 bytes per record; one resolver for every format and transport; Confluent framing
  for Avro and JSON Schema; Connect uses the same code.
- **Costs:** many settings with interactions (strategy, explicit coordinates, find-latest,
  auto-register); per-format differences in what can be derived from data.

## Example and verification

- **Example:** [`examples/kafka-serdes`](../examples/kafka-serdes/): `docker compose up -d && ./demo.sh`
  runs a tour of every behaviour above with Avro, JSON Schema and Protobuf, checks the bytes and
  headers on the topics and the artifacts in the registry, and runs Kafka Connect with the Avro
  converter. CI runs it against each pull request's images and code.
- **User documentation:**
  [`assembly-configuring-kafka-client-serdes.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-configuring-kafka-client-serdes.adoc),
  [`assembly-using-kafka-client-serdes.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-using-kafka-client-serdes.adoc).

| Concern | Tests to read |
|---|---|
| Resolver and cache | `DefaultSchemaResolverTest`, `AbstractSchemaResolverTest`, `ERCacheTest` (schema-resolver) |
| ID handlers and headers | `BaseSerdeTest`, `OptimisticFallbackIdHandlerTest` (serde-common); `DefaultHeadersHandlerTest` (serde-kafka-common) |
| Formats against a registry | `AvroSerdeTest`, `JsonSchemaSerdeTest`, `ProtobufSerdeTest` (app) |
| Confluent interop | `ConfluentSerdeTest` (app), `ConfluentBaseIT` (integration-tests) |
| Kafka end to end | `AvroSerdeIT`, `JsonSchemaSerdeIT`, `ProtobufSerdeIT` (integration-tests) |
| Converters | `SerdeBasedConverterTest`, `ProtobufConverterTest`, `ExtJsonConverterTest` (utils/converter); `RegistryConverterIT` |

```bash
./mvnw test -pl schema-resolver,serdes/generic/serde-common
```

## History

| Milestone | Result |
|---|---|
| 3.0 | Content ID and 4-byte IDs by default (2.x used the global ID in 8 bytes) |
| This ADR | First written record, with a verified example |
