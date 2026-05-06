# Apicurio Registry Integration Tests

This module contains integration tests for Apicurio Registry. Tests are organized
into groups using JUnit 5 `@Tag` annotations, and can be run against different
storage backends and deployment modes.

## Quick Start

The integration-tests module is not built by default. To include it, activate the
`-Pintegration-tests` profile. The project must be built first (`mvn clean install -DskipTests`).

```bash
# Run default test groups (smoke + serdes + acceptance) locally
./mvnw verify -Pintegration-tests -pl integration-tests -am

# Run a specific test group
./mvnw verify -Pintegration-tests -Dgroups=auth -pl integration-tests -am

# Run against a remote in-memory deployment (Kubernetes)
./mvnw verify -Pintegration-tests -Premote-mem -pl integration-tests -am

# Run against custom infrastructure (e.g., a Docker container)
docker run -d -p 8080:8080 quay.io/apicurio/apicurio-registry:latest-snapshot
./mvnw verify -Pintegration-tests -Dgroups=smoke \
  -Dquarkus.http.test-host=127.0.0.1 -Dquarkus.http.test-port=8080 \
  -pl integration-tests -am
```

## Test Groups

Tests are selected using the `-Dgroups` property. If not specified, the default
groups (`smoke | serdes | acceptance`) are used. These default groups do not require
any extra infrastructure.

| Group                     | Description                                      | Infrastructure          |
|---------------------------|--------------------------------------------------|-------------------------|
| **No extra infrastructure** | | |
| `smoke`                   | Basic functionality tests                        | None                    |
| `serdes`                  | Serialization/deserialization and converter tests | None                    |
| `acceptance`              | Acceptance tests                                 | None                    |
| `iceberg`                 | Iceberg REST Catalog tests                       | None (auto-configured)  |
| **Docker-based infrastructure** | | |
| `auth`                    | Authentication tests                             | Docker (Keycloak)       |
| `migration`               | Data migration between registry versions         | Docker (two registry instances) |
| `search`                  | Search tests                                     | Docker (Elasticsearch)  |
| `kafkasql-snapshotting`   | KafkaSQL snapshotting tests                      | Docker (Kafka)          |
| `debezium`                | Debezium PostgreSQL CDC tests                    | Docker (Kafka, PostgreSQL, Debezium) |
| `debezium-snapshot`       | Debezium PostgreSQL snapshot CDC tests           | Docker (Kafka, PostgreSQL, Debezium) |
| `debezium-mysql`          | Debezium MySQL CDC tests                         | Docker (Kafka, MySQL, Debezium) |
| `debezium-mysql-snapshot` | Debezium MySQL snapshot CDC tests                | Docker (Kafka, MySQL, Debezium) |
| **Kubernetes cluster** | | |
| `kubernetesops`         | Kubernetes operator integration tests            | Kubernetes cluster      |

Multiple groups can be combined using JUnit 5 tag expressions:

```bash
# Run smoke and auth tests
./mvnw verify -Pintegration-tests -Dgroups="smoke | auth" -pl integration-tests -am
```

## Deployment Modes

The integration tests support four ways to run the registry under test:

| Mode | How it works | Used by |
|------|-------------|---------|
| **Local JAR** (default) | `@QuarkusIntegrationTest` starts the app JAR as a separate process | Developers |
| **Local Docker** | `@QuarkusIntegrationTest` runs a pre-built Docker image (automatic when container image was built with `-Dquarkus.container-image.build=true`) | Developers |
| **Kubernetes** | `RegistryDeploymentManager` deploys the registry to a cluster via `-Premote-*` profiles | CI (minikube) |
| **External** | Tests point at an already-running registry via `-Dquarkus.http.test-host` and `-Dquarkus.http.test-port` | Manual testing, OpenShift |

The local JAR vs Docker distinction is handled automatically by the Quarkus test
framework based on whether a container image was produced during the build.

### Kubernetes deployment profiles

For Kubernetes mode, use one of the `remote-*` profiles to select the storage variant:

| Profile                  | Storage variant deployed to the cluster |
|--------------------------|-----------------------------------------|
| `-Premote-mem`           | In-memory                               |
| `-Premote-sql`           | PostgreSQL                              |
| `-Premote-kafka`         | KafkaSQL                                |
| `-Premote-kubernetesops` | Kubernetes operator                     |

The registry Docker image is deployed to the cluster. The image can be overridden:

```bash
./mvnw verify -Pintegration-tests -Premote-mem \
  -Dregistry-image=apicurio/apicurio-registry:my-tag \
  -pl integration-tests -am
```

## Auto-configured Infrastructure

Some test groups require additional infrastructure or configuration beyond just
group selection. `RegistryDeploymentManager` auto-detects the active groups and
configures the test environment accordingly:

| Group pattern | Auto-configuration |
|---------------|-------------------|
| `debezium*` | Deploys all Debezium infrastructure (Kafka, PostgreSQL, MySQL, Debezium Connect) |
| `iceberg` | Enables experimental feature flags (`apicurio.iceberg.enabled`) |
| `openshift` | Enables OpenShift-specific resources and routing |

No special profiles are needed - just pass `-Dgroups=...` as usual.

## Helper Script

The `run-tests.sh` script provides a convenient way to run tests against a
deployed registry instance:

```bash
./integration-tests/run-tests.sh --testGroups smoke --registryHost localhost --registryPort 8080
```

If `--testGroups` is omitted, the default groups are used. See `--help` for all options.

## Internals

The integration tests use JUnit 5 and `@QuarkusIntegrationTest`. The main entry
point for infrastructure deployment is
[`RegistryDeploymentManager`](src/test/java/io/apicurio/deployment/RegistryDeploymentManager.java),
which handles deploying the registry and required infrastructure to a Kubernetes
cluster when running in remote mode.
