# General Technical Review - Apicurio Registry / Sandbox

- **Project:** Apicurio Registry
- **Project Version:** 3.1.7 (latest stable), 3.2.0-SNAPSHOT (development)
- **Website:** https://www.apicur.io/registry
- **Date Updated:** 2026-02-26
- **Template Version:** v1.0
- **Description:** Open-source API and Schema registry for governing schemas, API definitions, and AI agent artifacts in cloud-native environments.

---

## Day 0 - Planning Phase

### Scope

**Describe the roadmap process, how scope is determined for mid to long term features, as well as how the roadmap maps back to current contributions and maintainer ladder?**

The roadmap is maintained as a [GitHub Project board](https://github.com/orgs/Apicurio/projects/22/views/1). Feature scope is determined through a combination of:
- Community feedback via GitHub issues and discussions
- Production requirements from downstream distributions (Red Hat build of Apicurio Registry, IBM Event Streams)
- Alignment with CNCF ecosystem standards (xRegistry specification, CloudEvents)
- Maintainer consensus during regular planning sessions

Contributors can progress through the maintainer ladder by consistently contributing quality code, reviews, and community engagement. The current maintainers are listed in the [project pom.xml](https://github.com/Apicurio/apicurio-registry/blob/main/pom.xml#L27-L69).

**Describe the target persona or user(s) for the project?**

- **Platform engineers** who operate schema and API registries as shared infrastructure for development teams
- **Application developers** who produce and consume schemas/APIs in microservices and event-driven architectures
- **Data engineers** who manage schema evolution in streaming data pipelines (Kafka, Pulsar, NATS)
- **API architects** who govern API design standards and compatibility across an organization
- **AI/ML engineers** who manage agent cards, prompt templates, and model schemas in AI-native applications

**Explain the primary use case for the project. What additional use cases are supported by the project?**

**Primary use case — Schema Registry:** Central management of serialization schemas (Avro, Protobuf, JSON Schema) for event-driven architectures. Producers and consumers resolve schemas at runtime via the registry, decoupling schema lifecycle from application deployment. The registry enforces compatibility rules to prevent breaking changes.

**Additional use cases:**
- **API Registry:** Store and govern API definitions (OpenAPI, AsyncAPI, GraphQL) with versioning, validation, and auto-generated documentation.
- **AI Agent Registry:** Store and discover A2A Agent Cards, MCP prompt templates, and LLM model schemas with the same governance guarantees as traditional schemas.
- **Contract testing:** Enforce compatibility rules between service versions to enable independent deployment.
- **Schema evolution governance:** Multi-level rules (global, group, artifact) for validity, compatibility, and integrity enforcement.
- **Migration from proprietary registries:** Confluent Schema Registry API compatibility (v7/v8) enables drop-in replacement.

**Explain which use cases have been identified as unsupported by the project.**

- General-purpose artifact storage (use OCI registries or Artifact Hub instead)
- Container image registry (use Harbor or similar)
- Service mesh configuration management
- API gateway functionality (routing, rate limiting, etc.)
- Runtime schema enforcement at the network level (the registry provides schemas; enforcement is the responsibility of serializer/deserializer libraries or application code)

**Describe the intended types of organizations who would benefit from adopting this project.**

- Organizations operating event-driven microservices architectures with Apache Kafka, Pulsar, or NATS
- Financial services, telecommunications, and e-commerce companies with strict schema evolution requirements
- Platform engineering teams providing shared developer infrastructure
- Organizations migrating from proprietary schema registries (Confluent, AWS Glue, Azure Schema Registry) to open-source alternatives
- AI-native organizations managing agent ecosystems with governance requirements

**Please describe any completed end user research and link to any reports.**

Community feedback is gathered through GitHub issues, discussions, and direct engagement at conferences (KubeCon, DevNation). The project roadmap reflects production requirements from downstream distributions that serve enterprise customers across financial services, telecommunications, and technology sectors.

### Usability

**How should the target personas interact with your project?**

- **Web UI:** Browse, search, create, and manage artifacts, versions, groups, and rules through a React-based web application built with PatternFly.
- **REST API:** Full CRUD operations via the Registry v3 REST API. OpenAPI specification available at `/apis/registry/v3`.
- **Client SDKs:** Java, Go, and TypeScript SDKs generated from the OpenAPI spec via Kiota.
- **CLI:** Command-line interface for scripting and automation.
- **Serializer/Deserializer libraries:** Transparent schema resolution integrated into Kafka, Pulsar, and NATS client libraries.
- **Maven plugin:** Schema registration and download as part of the build process.
- **Kubernetes operator:** Declarative deployment and lifecycle management via CRDs.
- **MCP Server:** AI/LLM tools can interact with the registry via the Model Context Protocol.

**Describe the user experience (UX) and user interface (UI) of the project.**

The web UI provides:
- Dashboard with global artifact and rule overview
- Artifact browser with search, filtering by group/type/labels, and pagination
- Artifact detail view with version history, metadata, content viewer/editor, and rule configuration
- Group management for organizing artifacts
- API design editing (integrated from the former Apicurio Studio project)
- Role-based access control reflected in the UI when authentication is enabled

The UI is built with React 19, PatternFly v6, and deployed as a separate container or served from the main application.

**Describe how this project integrates with other projects in production environments.**

- **Apache Kafka / Strimzi:** Schema registry for Kafka topics; KafkaSQL storage backend using Kafka itself as the data store
- **Pulsar / NATS:** Schema resolution via dedicated serdes libraries
- **Kubernetes:** Operator for lifecycle management; KubernetesOps storage variant for managing artifacts as ConfigMaps ([#7400](https://github.com/Apicurio/apicurio-registry/pull/7400))
- **CI/CD pipelines:** Maven plugin for schema registration during builds
- **OpenTelemetry:** Distributed tracing and metrics export
- **OIDC providers:** Keycloak, Azure Entra ID, Okta for authentication
- **CloudEvents:** The registry can be extended to support CloudEvents as an artifact type

### Design

**Explain the design principles and best practices the project follows.**

- **Single responsibility:** The registry manages artifact metadata, versioning, and governance. It does not enforce schemas at runtime — that responsibility belongs to serdes libraries and application code.
- **Pluggable storage:** Storage is abstracted behind the `RegistryStorage` interface. Implementations exist for SQL (PostgreSQL, H2, MSSQL, MySQL), KafkaSQL, and GitOps.
- **Decorator pattern:** Storage implementations use a decorator chain for cross-cutting concerns (logging, metrics, authorization) without coupling them to storage logic.
- **API-first:** The REST API is defined via OpenAPI and client SDKs are generated from it.
- **Configuration via environment variables:** Following 12-factor app principles, all configuration is externalized.
- **Upstream-first development:** All features are developed in the open-source project before being incorporated into downstream products.

**Outline or link to the project's architecture requirements.**

Architecture documentation: https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/index.html

The application is a single Quarkus-based Java application:
- **Minimum requirements:** 512MB RAM, 1 CPU core (development/PoC)
- **Production requirements:** 1-2GB RAM, 2+ CPU cores, external PostgreSQL or Kafka cluster
- **Storage:** No local persistent storage required — all state is in the configured storage backend

**Define any specific service dependencies the project relies on in the cluster.**

- **PostgreSQL** (for SQL storage mode) — any PostgreSQL 12+ instance
- **Apache Kafka** (for KafkaSQL storage mode) — any Kafka 3.x cluster
- **Git repository** (for GitOps storage mode)
- **OIDC provider** (optional, for authentication) — Keycloak, Azure Entra ID, Okta, or any OIDC-compliant provider

For development/testing, an in-memory H2 database is used with no external dependencies.

**Describe how the project implements Identity and Access Management.**

Apicurio Registry supports OIDC-based authentication via Quarkus OIDC, compatible with any OpenID Connect provider (Keycloak, Azure Entra ID, Okta, etc.). Authorization is role-based with three built-in roles:
- **sr-admin:** Full administrative access
- **sr-developer:** Create, read, update artifacts
- **sr-readonly:** Read-only access

Roles are mapped from OIDC token claims (configurable). Owner-based authorization can optionally restrict modification of artifacts to their creators. When authentication is disabled (default for development), all operations are permitted.

**Describe how the project has addressed sovereignty.**

Apicurio Registry is fully self-hosted — all data remains within the deployer's infrastructure. There are no external service calls, telemetry, or data exfiltration. The choice of storage backend (PostgreSQL, Kafka, GitOps) gives deployers full control over data residency and jurisdiction.

**Describe any compliance requirements addressed by the project.**

The project does not target specific compliance frameworks directly but enables compliance through:
- Audit logging of all operations
- Role-based access control
- Version immutability (non-draft versions cannot be modified)
- Data sovereignty (fully self-hosted, no external dependencies)

**Describe the project's High Availability requirements.**

Apicurio Registry is stateless (all state in the storage backend), enabling horizontal scaling:
- **SQL mode:** Multiple registry instances behind a load balancer, sharing a PostgreSQL database
- **KafkaSQL mode:** Multiple instances sharing a Kafka topic, with each instance maintaining a local materialized view. Kafka provides the durability guarantees.
- **GitOps mode:** Multiple instances reading from the same Git repository

HA for the storage backend itself (PostgreSQL replication, Kafka cluster) is outside the registry's scope.

**Describe the project's resource requirements, including CPU, Network and Memory.**

| Environment | CPU | Memory | Network |
|---|---|---|---|
| Development (H2) | 1 core | 512MB | Loopback only |
| Production (SQL) | 2+ cores | 1-2GB | Access to PostgreSQL |
| Production (KafkaSQL) | 2+ cores | 1-2GB | Access to Kafka cluster |
| Production (high traffic) | 4+ cores | 2-4GB | Load balancer, storage backend |

**Describe the project's storage requirements, including its use of ephemeral and/or persistent storage.**

The application itself requires **no persistent local storage**. All state is stored in the configured backend:
- **SQL:** PostgreSQL database (schema auto-created on startup)
- **KafkaSQL:** Kafka topics (auto-created or pre-provisioned)
- **GitOps:** Git repository

Temporary/ephemeral storage is used only for JVM temp files and logging.

**Please outline the project's API Design:**

*Describe the project's API topology and conventions:*
The primary API is the Registry v3 REST API, a RESTful JSON API following standard HTTP conventions (GET/POST/PUT/DELETE, standard status codes, JSON request/response bodies). The API is organized hierarchically: Groups > Artifacts > Versions. An OpenAPI 3.0 specification is published and available at runtime at `/apis/registry/v3`.

Additionally, the project implements the Confluent Schema Registry API (v7 and v8) at `/apis/ccompat/v7` and `/apis/ccompat/v8` for drop-in compatibility with existing Kafka ecosystems.

*Describe the project defaults:*
- In-memory H2 storage (development mode)
- No authentication (development mode)
- All content rules disabled
- CORS enabled for localhost origins

*Outline any additional configurations from default to make reasonable use of the project:*
- Configure a persistent storage backend (`apicurio.storage.kind=sql` or `kafkasql`)
- Enable OIDC authentication for production
- Configure global validity and compatibility rules
- Set up TLS termination (typically via ingress/load balancer)

*Describe compatibility of any new or changed APIs with API servers, including the Kubernetes API server:*
The Registry v3 API does not interact with the Kubernetes API server. The Kubernetes operator uses standard CRDs for registry deployment and lifecycle management. The KubernetesOps storage variant ([#7400](https://github.com/Apicurio/apicurio-registry/pull/7400)) reads artifacts from Kubernetes ConfigMaps using a read-only polling model, following the same proven pattern as the GitOps storage backend.

*Describe versioning of any new or changed APIs, including how breaking changes are handled:*
The REST API is versioned (v2, v3). Breaking changes are only introduced in major versions. The v2 API remains available for backward compatibility. Deprecation notices are published in release notes at least one major version before removal.

**Describe the project's release processes, including major, minor and patch releases.**

- **Patch releases (3.1.x):** Monthly cadence, bug fixes and dependency updates. No breaking changes.
- **Minor releases (3.x.0):** Feature releases, approximately every 6 months. May include new APIs or artifact types but no breaking changes to existing APIs.
- **Major releases (x.0.0):** May include breaking API changes. Migration guides are published. Previous major version APIs remain available for a transition period.

Releases are published to Maven Central, Docker Hub (`apicurio/apicurio-registry`), and Quay.io. Release notes are published on GitHub.

### Installation

**Describe how the project is installed and initialized.**

**Container (simplest):**
```bash
docker run -it -p 8080:8080 apicurio/apicurio-registry:latest-snapshot
```

**Kubernetes (operator):**
Deploy the operator via OLM or Helm, then create an `ApicurioRegistry` custom resource specifying the desired storage backend and configuration.

**Kubernetes (direct):**
Deploy the container image with appropriate environment variables for storage configuration, optionally with a `Service`, `Ingress`, and `ConfigMap`.

**Development:**
```bash
./mvnw clean install -DskipTests && cd app && ../mvnw quarkus:dev
```

**How does an adopter test and validate the installation?**

- Health endpoint: `GET /health/ready` returns 200 when the application is ready
- Liveness endpoint: `GET /health/live`
- System info: `GET /apis/registry/v3/system/info` returns version and configuration
- Create a test artifact via the REST API or web UI
- Run the provided integration test suite against the deployment

### Security

**Please provide a link to the project's cloud native security self assessment.**

A formal CNCF security self-assessment has not yet been completed. We plan to complete one as part of the Sandbox onboarding process.

**Security Hygiene:**

- Dependencies are managed via Renovate with automated PRs for updates
- The project uses Quarkus's built-in security framework for OIDC, RBAC, and CORS
- Container images are based on Red Hat UBI (Universal Base Image) minimal images
- The CI pipeline runs Checkstyle and linting on every PR
- GitHub Security Advisories are used for vulnerability disclosure

**Cloud Native Threat Modeling:**

*Explain the least minimal privileges required by the project:*
- The application runs as a non-root user in the container
- Requires network access to the storage backend (PostgreSQL/Kafka) and optionally an OIDC provider
- No cluster-wide Kubernetes permissions required for the application itself (the operator requires namespace-scoped permissions for managing deployments)

*Describe how the project handles certificate rotation:*
TLS termination is typically handled by the ingress controller or load balancer. The application supports TLS configuration via Quarkus properties for direct TLS termination. Certificate rotation is handled by the deployer's infrastructure (cert-manager, etc.).

*Describe how the project implements secure software supply chain best practices:*
- All releases are built in CI (GitHub Actions) with reproducible builds
- Container images are published to Docker Hub and Quay.io with digest-based references
- Maven artifacts are published to Maven Central with GPG signatures
- The project uses a `pom.xml`-based dependency management with explicit version pinning
