# Apicurio Registry Integration Tests

This module contains integration tests for Apicurio Registry. Tests are organized
into groups using JUnit 5 `@Tag` annotations, and can be run against different
storage backends and deployment modes.

## Quick Start

The integration-tests module is not built by default. To include it, activate the
`-Pintegration-tests` profile. The project must be built first (`mvn clean install -DskipTests`).

```bash
# Run default test groups (smoke + serdes + acceptance) with local app
./mvnw verify -Pintegration-tests -Plocal-tests -pl integration-tests -am

# Run a specific test group
./mvnw verify -Pintegration-tests -Plocal-tests -Dgroups=auth -pl integration-tests -am

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
| `iceberg`                 | Iceberg REST Catalog tests                       | None (needs feature flag, see `-Piceberg`) |
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
./mvnw verify -Pintegration-tests -Plocal-tests -Dgroups="smoke | auth" -pl integration-tests -am
```

## Deployment Profiles

These profiles configure **how** the registry is deployed for testing:

| Profile               | Description                                      |
|-----------------------|--------------------------------------------------|
| `-Plocal-tests`       | Run the registry embedded in the test JVM via `@QuarkusIntegrationTest` |
| `-Premote-mem`        | Deploy in-memory registry to a Kubernetes cluster |
| `-Premote-sql`        | Deploy SQL registry to a Kubernetes cluster      |
| `-Premote-kafka`      | Deploy KafkaSQL registry to a Kubernetes cluster |
| `-Premote-kubernetesops` | Deploy via Kubernetes operator                |

When no deployment profile is specified, you can point tests at any running registry
using `-Dquarkus.http.test-host` and `-Dquarkus.http.test-port`.

The `remote-*` profiles deploy Docker images to the cluster. The image can be overridden:

```bash
./mvnw verify -Pintegration-tests -Premote-mem \
  -Dregistry-in-memory-image=apicurio/apicurio-registry:my-tag \
  -pl integration-tests -am
```

## Special Profiles

Some test groups require infrastructure configuration beyond group selection.
These are activated using Maven profiles:

| Profile          | Groups activated                                                          | Extra configuration                     |
|------------------|---------------------------------------------------------------------------|----------------------------------------|
| `-Pdebezium-all` | `debezium,debezium-snapshot,debezium-mysql,debezium-mysql-snapshot`        | Deploys all Debezium infrastructure    |
| `-Piceberg`      | `iceberg`                                                                 | Enables experimental feature flags     |
| `-Popenshift`    | *(none)*                                                                  | Enables OpenShift-specific resources   |

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
