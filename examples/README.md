# Apicurio Registry examples

Runnable examples, grouped by feature. Each one starts what it needs with Docker Compose (or a kind
cluster), and has:

- a `README.md` with a quick start;
- a `GUIDE.md` that walks through the feature step by step;
- a `demo.sh` that checks every step and ends with `All checks passed.`.

CI runs every `demo.sh` on each pull request against the images built from that pull request, so
the examples follow the code. Examples marked *not checked yet* predate this and are being brought
up to the same standard.

Each row links the decision record (ADR) that explains the design, and the user documentation.

Most examples start with `docker compose up -d` and then `./demo.sh`. Some start what they need
themselves (`operator` creates a kind cluster, `storage-variants` picks Compose profiles), so
follow each example's README quick start.

## Clients

| Example | What it shows | ADR | Docs |
|---|---|---|---|
| [registry-clients](registry-clients/) | One scenario with the Java, Go, Python and TypeScript SDKs and the CLI | | [SDKs](../docs/modules/ROOT/pages/getting-started/assembly-using-the-registry-client.adoc), [CLI](../docs/modules/ROOT/pages/getting-started/assembly-managing-registry-artifacts-cli.adoc) |

## Schemas and governance

| Example | What it shows | ADR | Docs |
|---|---|---|---|
| [rules-and-compatibility](rules-and-compatibility/) | Validity, compatibility and integrity rules, globally, per group and per artifact; the client-side validation library | [0006](../adr/0006-rules-engine.md) | [Rules](../docs/modules/ROOT/pages/getting-started/assembly-intro-to-registry-rules.adoc) |
| [schema-references](schema-references/) | References from the Maven plugin, the REST API and the serializers; the reference graph and integrity rules | [0008](../adr/0008-artifact-references.md) | [References](../docs/modules/ROOT/pages/getting-started/assembly-artifact-reference.adoc) |
| [custom-artifact-types](custom-artifact-types/) | Adding artifact types with Java classes or a webhook | [0013](../adr/0013-custom-artifact-types.md) | [Custom types](../docs/modules/ROOT/pages/getting-started/assembly-custom-artifact-types.adoc) |
| [odcs-data-contracts](odcs-data-contracts/) | ODCS contracts projected onto schemas, enforced by serdes, migration rules | [0002](../adr/0002-odcs-native-data-contracts.md) | [Data contracts](../docs/modules/ROOT/pages/getting-started/assembly-data-contracts.adoc) |

## Kafka

| Example | What it shows | ADR | Docs |
|---|---|---|---|
| [kafka-serdes](kafka-serdes/) | Avro, Protobuf and JSON Schema serdes, strategies, Kafka Connect | [0007](../adr/0007-kafka-serdes.md) | [Serdes](../docs/modules/ROOT/pages/getting-started/assembly-configuring-kafka-client-serdes.adoc) |
| [confluent-compatibility](confluent-compatibility/) | Confluent clients and tools against the Confluent-compatible API, and migrating from Confluent | [0001](../adr/0001-confluent-schema-registry-compatibility.md) | [Confluent compatibility](../docs/modules/ROOT/pages/getting-started/assembly-confluent-schema-registry-compatibility.adoc) |
| [camel-quarkus-kafka](camel-quarkus-kafka/) | Camel Quarkus routes with the registry's Avro serdes. *Not checked yet* | | |

## Security

| Example | What it shows | ADR | Docs |
|---|---|---|---|
| [authentication](authentication/) | OIDC, HTTP basic, an authenticating proxy and Kubernetes tokens, from the Java SDK, serdes, Maven plugin and CLI | [0009](../adr/0009-authentication.md) | [Security](../docs/modules/ROOT/pages/getting-started/assembly-configuring-registry-security.adoc) |
| [per-resource-authorization](per-resource-authorization/) | Access rules per group and artifact, filtered search | [0005](../adr/0005-per-resource-authorization.md) | [Per-resource authorization](../docs/modules/ROOT/pages/getting-started/assembly-configuring-resource-authorization.adoc) |
| [tls](tls/) | HTTPS, mutual TLS through a proxy, and the Java SDK, Kafka serdes and Maven plugin with PKCS12, JKS and PEM stores, including the refusals | [0009](../adr/0009-authentication.md) | [Maven plugin](../docs/modules/ROOT/pages/getting-started/assembly-managing-registry-artifacts-maven.adoc), [SDK](../docs/modules/ROOT/pages/getting-started/assembly-using-the-registry-client.adoc), [serdes](../docs/modules/ROOT/pages/getting-started/assembly-configuring-kafka-client-serdes.adoc) |

## Deploying and operating

| Example | What it shows | ADR | Docs |
|---|---|---|---|
| [storage-variants](storage-variants/) | The same checks on in-memory, PostgreSQL, MySQL, SQL Server, KafkaSQL, GitOps and KubernetesOps storage, and Elasticsearch search | [0010](../adr/0010-storage-architecture.md), [0015](../adr/0015-search-and-elasticsearch.md) | [Installing](../docs/modules/ROOT/pages/getting-started/assembly-installing-registry-docker.adoc) |
| [operator](operator/) | The Kubernetes operator on kind: a custom resource per storage | [0019](../adr/0019-operator.md), [0020](../adr/0020-multitenancy.md) | [Operator](../operator/docs/modules/ROOT/pages/) |
| [operational-guardrails](operational-guardrails/) | Limits, disabled APIs, deletion switches, read-only mode | [0017](../adr/0017-operational-guardrails.md) | [Configuration](../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc) |
| [http-caching](http-caching/) | Cache headers and ETags per endpoint: what is safe to cache, and what is not | [0016](../adr/0016-http-caching.md) | |
| [registry-events](registry-events/) | Change events from SQL storage (an outbox table) and from KafkaSQL, and CloudEvents | [0011](../adr/0011-registry-events.md) | [Events](../docs/modules/ROOT/pages/getting-started/assembly-registry-events.adoc) |
| [usage-telemetry-demo](usage-telemetry-demo/) | Which schemas clients use, and which are safe to retire | [0003](../adr/0003-usage-telemetry.md) | [Usage telemetry](../docs/modules/ROOT/pages/getting-started/assembly-usage-telemetry.adoc) |
| [v2-to-v3-migration](v2-to-v3-migration/) | Exporting from 2.6 and importing into 3.x with IDs kept; backup and restore within 3.x | [0012](../adr/0012-import-export-and-migration.md) | [Migrating](../docs/modules/ROOT/pages/getting-started/assembly-migrating-registry-v2-v3.adoc) |
| [otel-tracing](otel-tracing/) | One OpenTelemetry trace from Kafka producers and Debezium change data capture, through the registry, to consumers, checked in Jaeger | [0007](../adr/0007-kafka-serdes.md) | [Configuration](../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc) |

## AI agents

| Example | What it shows | ADR | Docs |
|---|---|---|---|
| [agent-registry](agent-registry/) | Agent cards, MCP tools, model schemas and prompt templates; discovery and rendering | [0014](../adr/0014-agent-registry.md), [0004](../adr/0004-ai-catalog-ard-convergence.md) | [Agent registry](../docs/modules/ROOT/pages/getting-started/assembly-agent-registry-quickstart.adoc) |
| [mcp-keycloak](mcp-keycloak/) | The registry's MCP server against a Keycloak-secured registry. *Not checked yet* | | [MCP server](../docs/modules/ROOT/pages/getting-started/assembly-mcp-server-integration.adoc) |

## Other

- [`tools/`](tools/): utilities, not examples. `kafkasql-topic-import` loads an exported KafkaSQL
  topic back into Kafka (see
  [exporting KafkaSQL topic data](../docs/modules/ROOT/pages/getting-started/guide-exporting-registry-kafka-topic-data.adoc)).

## Writing an example

Start from [`_template`](_template/) and follow [TEMPLATE.md](TEMPLATE.md): the files an example
needs, how `demo.sh` checks, and the `ci.env` settings that CI reads.
