# Apicurio Registry Example Applications
This repository contains a set of example applications (mostly Kafka applications) that use the
Apicurio Registry as part of their workflow.  The registry is typically used to store schemas
used by Kafka serializer and deserializer classes.  These serdes classes will fetch the schema
from the registry for use during producing or consuming operations (to serializer, deserializer,
or validate the Kafka message payload).

Each example in this repository attempts to demonstrate some specific use-case or configuration.
There are numerous options available when integrating with the registry, and therefore the set
of examples found here may not cover every configuration permutation.

These examples rely on an Apicurio Registry server being available, you can start one with the latest build by running:

```bash
docker run -p 8080:8080 -it apicurio/apicurio-registry:latest-snapshot
```

This command will start an Apicurio Registry server and make it available at the port 8080 of localhost.

Some examples also require a Kafka Cluster available, you can very easily run one using the [docker-compose resources available in the tools folder](tools/kafka-compose/kafka-compose.yaml)

Simply run `docker compose -f examples/tools/kafka-compose/kafka-compose.yaml up` from the repository root and you'll have a Kafka broker available at the port 9092.

# List of Examples
A list of examples is included below, with descriptions and explanations of each covered use-case.

## Kafka Serdes Example
This example runs Kafka, Apicurio Registry and its web console with Docker Compose, and a tour of
Java producers and consumers using the Avro, JSON Schema and Protobuf serializers: auto-registration,
the wire format and headers mode, generated and plain Java classes, naming strategies (built-in and
custom), a custom resolver, schema evolution, a compatibility rule rejecting a schema, a schema
registered by the Maven plugin, and Kafka Connect with the Apicurio converters. Its
[step-by-step guide](kafka-serdes/GUIDE.md) explains each step.

## Schema References Example
This example registers Avro, JSON Schema and AsyncAPI schemas that reference each other with the
Maven plugin (references spelled out and found automatically), and shows how the registry lists,
graphs, dereferences, rewrites and hashes references, what integrity rules and deletion do, and how
Kafka serializers register nested Avro types and Protobuf imports. Its
[step-by-step guide](schema-references/GUIDE.md) explains each step.

## Authentication Example
This example runs Keycloak and four registries with Docker Compose, each authenticating callers
differently: OIDC tokens for users and service accounts (with roles, basic credentials exchanged for
tokens, and the web console login), HTTP basic against users configured in the registry, identity
from an authenticating nginx proxy, and Kubernetes service account tokens checked against a k3s
server. The Java SDK, Kafka serializers, the Maven plugin and the CLI authenticate too. Its
[step-by-step guide](authentication/GUIDE.md) covers every mechanism, including the rejections.

## Storage Variants Example
This example runs the same registry on every storage variant with Docker Compose profiles, with the
same checks on each: PostgreSQL with two replicas, MySQL, SQL Server, KafkaSQL with two replicas
sharing a journal topic, GitOps loading a git repository the demo commits to (including a broken
commit that is reported and not applied), and KubernetesOps loading ConfigMaps from k3s. Its
[step-by-step guide](storage-variants/GUIDE.md) explains how each one works and how to choose.

## Registry Events Example
This example makes a fixed sequence of changes in the registry and checks the events it publishes to
Kafka, on PostgreSQL (outbox table, Debezium outbox event router, delivery after an outage) and on
KafkaSQL (two replicas, one total order), and converts them to CloudEvents. Its
[step-by-step guide](registry-events/GUIDE.md) explains the guarantees of each.

## v2 to v3 Migration Example
This example seeds an Apicurio Registry 2.6 server, migrates it to 3.x with the documented export and
import scripts, and checks that every version keeps its globalId, contentId, state, content and
references, through the v3 API and the v2 API. It also covers the import guards, an import with new
IDs, backup and restore within 3.x, and 3.x refusing a 2.6 KafkaSQL topic. See its
[step-by-step guide](v2-to-v3-migration/GUIDE.md).

## Rules and Compatibility Example
This example runs Apicurio Registry and its web console with Docker Compose and shows its content
rules: validity, BACKWARD and FORWARD compatibility, which rule wins when rules are set globally, per
group and per artifact, dry runs, transitive checks, drafts and integrity rules for references. A
small Java client validates JSON Schema, Avro and Protobuf data against registered schemas with the
client-side validation library.
Its [step-by-step guide](rules-and-compatibility/GUIDE.md) walks through each rule in the REST API
and the UI.

## Per-resource Authorization Example
This example runs Apicurio Registry, its web console and Keycloak with Docker Compose, with
per-resource authorization: an access rules file decides which groups and artifacts each user can
read or write, on top of the Keycloak roles. Its [step-by-step guide](per-resource-authorization/GUIDE.md)
walks through each feature in the REST API and the UI, and works as a script for a team demo.

## AI Agent Registry Demo
This example runs every agent registry feature of Apicurio Registry with Docker Compose: Agent
Cards, MCP tools and servers, prompt templates and model schemas; references and compatibility
rules; agent and tool discovery, the AI Catalog and ARD; the MCP Registry API; importing tools from a
running MCP server; agent visibility and access rules with Keycloak; and the Registry MCP server.
Quarkus LangChain4j agents publish their Agent Cards and discover each other and an MCP server
through the Registry. Its [step-by-step guide](agent-registry/GUIDE.md) walks through each feature,
with screenshots, and works as a script for a team demo.

## ODCS Data Contracts Example
This example runs Apicurio Registry, its web console and Kafka with Docker Compose, and governs an
Avro schema with an Open Data Contract Standard (ODCS) contract: the registry projects the contract
onto the schema as labels, field tags and rules, and the Kafka serializer enforces those rules
before records are sent. Its [step-by-step guide](odcs-data-contracts/GUIDE.md) walks through each
feature in the REST API and the UI, and works as a script for a team demo.

## Confluent Compatibility Example
This example runs Confluent's own serializers and console tools (Avro, Protobuf, JSON Schema) against
Apicurio Registry's Confluent-compatible API, and checks the wire format and schema IDs, how subjects
map to artifacts and groups, references, compatibility, soft and permanent deletes, errors, and the
v7 and v8 APIs. See its [step-by-step guide](confluent-compatibility/GUIDE.md). Mixing Confluent's and
Apicurio's serdes in one system is the `confluent` step of the Kafka Serdes Example.

## Rest Client example
This example application demonstrates how to use the Apicurio Registry rest client to create,
delete, or fetch schemas. This example contains two basic java application, one showing how to
improve the logs by logging all the request and response headers and a basic example on how to
use the client.

## LLM/AI Model Schema Artifact Types
This example demonstrates the built-in AI/ML artifact types for managing LLM schemas and prompt templates:

- **MODEL_SCHEMA**: AI/ML model input/output schema definitions with capability metadata
- **PROMPT_TEMPLATE**: Version-controlled prompt templates with variable schemas

Features include content auto-detection, backward compatibility checking, server-side prompt rendering,
and model capability search. Includes integration examples for Quarkus + LangChain4j.

See the [llm-artifact-types](llm-artifact-types/) directory for details.

## MCP Server + Keycloak Example
Docker Compose stack for testing the Apicurio Registry MCP server against a Keycloak-secured Registry.
Includes stdio mode (client credentials) and HTTP mode (OAuth token forwarding).

See the [mcp-keycloak](mcp-keycloak/) directory for details.

## Kafka Order Processing Example
This example provides a complete, realistic Kafka architecture demonstrating both message production and
consumption with Apicurio Registry as the schema registry. The example simulates an order processing
system with separate producer and consumer applications built using Quarkus. The producer automatically
generates random orders every 5 seconds, while the consumer processes them in real-time. Both applications
use Avro serialization with schemas managed by Apicurio Registry, demonstrating automatic schema
registration, schema evolution support, and seamless integration between Kafka and the registry. Includes
a Docker Compose setup for running Kafka, Zookeeper, and Apicurio Registry locally.

See the [kafka-order-processing](kafka-order-processing/) directory for details.
## A2A Agent Card Example
This example demonstrates how to use Apicurio Registry to manage A2A (Agent-to-Agent) Agent Cards -
JSON metadata documents that describe AI agents according to the A2A Protocol. The example shows how
to register, version, and search for AI agent definitions, as well as apply compatibility rules to
ensure safe evolution of agent capabilities. This is useful for organizations building agentic AI
applications that need to discover and communicate with each other.

## A2A Real-World Integration Example
This example demonstrates **actual working A2A protocol integration** with Apicurio Registry. Unlike
simulated examples, this runs real HTTP servers that implement the A2A protocol and communicate over
the network. Features include:

- **Mock Agents**: Real HTTP servers on ports 9001-9003 implementing A2A endpoints
- **A2A Discovery**: Agents expose `/.well-known/agent.json` for capability discovery
- **A2A Tasks**: Agents accept `POST /a2a` with JSON-RPC for task execution
- **Orchestrator**: Makes real HTTP requests to discover and invoke agents
- **Registry Integration**: Agents registered and discovered via `/.well-known/agents`

The demo includes a multi-agent workflow that processes customer complaints through sentiment analysis,
summarization, and translation agents - all using real HTTP communication.

See the [a2a-real-world-integration](a2a-real-world-integration/) directory for details.

## Agent Registry Workflow with Quarkus LangChain4j

An end-to-end sample combining Registry-backed A2A agent publication, discovery and
summarize-to-translate delegation with MCP tool discovery and invocation. It also
demonstrates compatibility rejection and accepted version changes.

See [agent-registry-workflow](agent-registry-workflow/) for the external sample and
run instructions. The sample is currently available as a contributor-branch preview
in Quarkus LangChain4j.

## Custom Artifact Types (Java providers)

Adds a custom `MARKDOWN` artifact type to the registry with Java implementations of the artifact
type interfaces (content accepter, validator, compatibility checker, canonicalizer), configured
through `apicurio.artifact-types.config-file`. The providers are loaded into the
`apicurio/apicurio-registry:VERSION-mutable` container image by re-augmenting it, so no custom
build of the registry is needed. See the [custom-artifact-types](custom-artifact-types/) directory for details.
