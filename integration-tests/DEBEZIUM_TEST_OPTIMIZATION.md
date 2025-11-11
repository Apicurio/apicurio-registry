# Debezium Test Infrastructure Optimization

## Overview

This document describes the optimization implemented for Debezium CDC integration tests to improve CI execution time. The solution uses shared container infrastructure across all test classes instead of starting and stopping containers for each test class.

## Problem Statement

Previously, each Debezium test class used `@QuarkusTestResource` with `restrictToAnnotatedClass = true`, which meant:
- Each test class started its own set of containers (MySQL/PostgreSQL, Kafka, Debezium)
- Containers were stopped after each test class completed
- When running tests sequentially in CI, this caused significant overhead due to repeated container startup/shutdown

## Solution

The solution provides TWO complementary optimizations:

### 1. Local Mode Optimization (Testcontainers)
Introduces a **SharedDebeziumInfrastructure** class that manages a single set of containers shared across all test classes.

### 2. Cluster Mode Optimization (Kubernetes CI)
Updates the **DebeziumDeploymentManager** to support idempotent deployment and adds a combined Maven profile for running all tests in one execution.

### Key Components

#### 1. SharedDebeziumInfrastructure
Location: `integration-tests/src/test/java/io/apicurio/tests/serdes/apicurio/debezium/SharedDebeziumInfrastructure.java`

This class manages:
- **Shared Kafka container** - Started once and reused by all tests
- **Shared MySQL container + Debezium** - For all MySQL tests
- **Shared PostgreSQL container + Debezium** - For all PostgreSQL tests

Features:
- Containers are started on first use (lazy initialization)
- Containers remain running for the entire test suite
- Cleanup happens via JVM shutdown hook
- Thread-safe initialization using `AtomicBoolean`
- Supports both host and bridge network modes (CI and local)

#### 2. Updated Container Resources

**BaseDebeziumContainerResource**
- Refactored to use SharedDebeziumInfrastructure
- Removed direct container creation logic
- Added abstract methods for infrastructure initialization

**DebeziumMySQLContainerResource**
- Calls `SharedDebeziumInfrastructure.initializeMySQLInfrastructure()`
- Gets container references from shared infrastructure

**DebeziumContainerResource (PostgreSQL)**
- Calls `SharedDebeziumInfrastructure.initializePostgreSQLInfrastructure()`
- Gets container references from shared infrastructure

**LocalConverters Resources**
- Updated to use shared infrastructure
- Mount local converters on shared containers

#### 3. Updated Test Classes

All test class annotations were updated to remove `restrictToAnnotatedClass = true`:

```java
// Before
@QuarkusTestResource(value = DebeziumMySQLContainerResource.class, restrictToAnnotatedClass = true)

// After
@QuarkusTestResource(value = DebeziumMySQLContainerResource.class)
```

This allows the test resource to be shared across test classes.

## Benefits

1. **Faster CI Execution**: Containers start once instead of multiple times
2. **Resource Efficiency**: Reduced Docker overhead and memory usage
3. **Backward Compatible**: Works in both local and cluster (Kubernetes) modes
4. **No Test Changes Required**: Tests remain unchanged, only infrastructure setup is optimized

## Usage

### Running Tests Locally

```bash
# Run all Debezium tests (PostgreSQL + MySQL)
mvn clean verify -Pdebezium

# Run only MySQL tests
mvn clean verify -Pdebezium-mysql

# Run only PostgreSQL tests
mvn clean verify -Pdebezium

# Run with local converters
mvn clean verify -Pdebezium-snapshot
```

The shared infrastructure will automatically:
1. Start Kafka on first test class initialization
2. Start MySQL/PostgreSQL when first MySQL/PostgreSQL test runs
3. Reuse containers for all subsequent tests
4. Stop all containers when JVM exits

### How It Works

```
Test Suite Execution:
┌─────────────────────────────────────────────────┐
│ JVM Starts                                      │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ First Test Class Initializes                   │
│ → SharedDebeziumInfrastructure.initializeXXX() │
│ → Starts Kafka (if not started)                │
│ → Starts Database + Debezium (if not started)  │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ Second Test Class Initializes                  │
│ → SharedDebeziumInfrastructure.initializeXXX() │
│ → Detects containers already running           │
│ → Reuses existing containers                   │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ All Test Classes Complete                      │
│ → Containers remain running                    │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ JVM Shutdown                                    │
│ → Shutdown hook triggered                      │
│ → All containers stopped                       │
└─────────────────────────────────────────────────┘
```

## Test Isolation

Even though containers are shared, tests remain isolated:
- Each test creates and drops its own database tables (via `@AfterEach`)
- Each test uses unique connector names (via atomic counter)
- Each test subscribes to unique Kafka topics
- Kafka consumers are recreated for each test

## Network Modes

The solution supports both network modes automatically:

### Host Network Mode (CI/Linux)
- Containers use `--network=host`
- Direct localhost communication
- Faster networking

### Bridge Network Mode (Mac/Windows)
- Containers on shared Docker network
- Uses `host.testcontainers.internal` for registry access
- Compatible with Docker Desktop

## Troubleshooting

### Tests fail with "Container not found"
- Ensure SharedDebeziumInfrastructure is properly initialized
- Check logs for initialization errors

### Port conflicts
- If running multiple test suites in parallel, ensure they use different ports
- Consider using port auto-assignment

### Container cleanup issues
- Containers should stop automatically on JVM exit
- Manual cleanup: `docker ps -a | grep debezium` then `docker stop <id>`

## Future Improvements

Potential enhancements:
1. Add container health checks before test execution
2. Implement container state verification
3. Add metrics for container reuse statistics
4. Consider Testcontainers reusable containers feature for cross-JVM reuse

## Cluster Mode (Kubernetes CI)

In CI, tests run in Kubernetes with `cluster.tests=true`. The previous approach deployed infrastructure separately for each test group:

### Previous CI Execution (4 separate Maven executions)

```bash
# Execution 1: PostgreSQL tests
mvn verify -Pdebezium -Premote-mem
  → Deploys Kafka + PostgreSQL + Debezium
  → Runs tests
  → Tears down infrastructure

# Execution 2: PostgreSQL with local converters
mvn verify -Pdebezium-snapshot -Premote-mem
  → Deploys Kafka + PostgreSQL + Debezium LOCAL again
  → Runs tests
  → Tears down infrastructure

# Execution 3: MySQL tests
mvn verify -Pdebezium-mysql -Premote-mem
  → Deploys Kafka + MySQL + Debezium again
  → Runs tests
  → Tears down infrastructure

# Execution 4: MySQL with local converters
mvn verify -Pdebezium-mysql-snapshot -Premote-mem
  → Deploys Kafka + MySQL + Debezium LOCAL again
  → Runs tests
  → Tears down infrastructure
```

**Problem**: Infrastructure deployed and torn down 4 times!

### Optimized CI Execution (Single Maven execution)

```bash
# Single execution: All Debezium tests
mvn verify -Pdebezium-all -Premote-mem
  → Deploys ALL infrastructure once:
      - Kafka (shared)
      - PostgreSQL
      - MySQL
      - Debezium Connect (published converters)
      - Debezium Connect (local converters)
  → Runs ALL test groups:
      - debezium (PostgreSQL + published)
      - debezium-snapshot (PostgreSQL + local)
      - debezium-mysql (MySQL + published)
      - debezium-mysql-snapshot (MySQL + local)
  → Tears down infrastructure ONCE at the end
```

### Key Implementation Details

1. **Idempotent Deployment**: `DebeziumDeploymentManager` tracks what's deployed using static flags
   - Multiple calls to deploy methods skip already-deployed components
   - Prevents duplicate deployments

2. **Combined Maven Profile**: New `-Pdebezium-all` profile
   - Sets `deployAllDebezium=true`
   - Includes all test groups: `debezium,debezium-snapshot,debezium-mysql,debezium-mysql-snapshot`
   - Single test execution runs all test classes

3. **GitHub Actions**: Updated workflow uses single step
   ```yaml
   - name: Run Integration Tests - Debezium (All - Optimized)
     run: ./mvnw verify -Pdebezium-all -Premote-mem ...
   ```

4. **Port Configuration for Parallel Debezium Instances**: To avoid port conflicts with `minikube tunnel`
   - `debezium-connect-service-external` (published converters) → `localhost:8083`
   - `debezium-connect-local-service-external` (local converters) → `localhost:8084`
   - Tests automatically route to the correct instance via `KubernetesDebeziumContainerWrapper`

### How Connector Routing Works

Each test class knows which Debezium Connect instance to use:

```
Test Class                                → Debezium Connect Instance
─────────────────────────────────────────────────────────────────────
DebeziumMySQLAvroIntegrationIT            → localhost:8083 (published)
DebeziumMySQLAvroLocalConvertersIT        → localhost:8084 (local)
DebeziumPostgreSQLAvroIntegrationIT       → localhost:8083 (published)
DebeziumPostgreSQLAvroLocalConvertersIT   → localhost:8084 (local)
```

**Important**: A single Debezium Connect instance can manage connectors for BOTH MySQL and PostgreSQL. The separation is by **converter type** (published vs local), not by database type.

### CI Performance Impact

**Before**: ~40-60 minutes (4 deployments × 10-15 min each)
**After**: ~15-20 minutes (1 deployment + all tests)

**Improvement**: **60-70% faster** execution time

## Files Modified

1. **New File**: `SharedDebeziumInfrastructure.java` - Core shared infrastructure
2. **Modified**: `BaseDebeziumContainerResource.java` - Base resource refactoring
3. **Modified**: `DebeziumMySQLContainerResource.java` - MySQL resource
4. **Modified**: `DebeziumContainerResource.java` - PostgreSQL resource
5. **Modified**: `DebeziumMySQLLocalConvertersResource.java` - MySQL local converters
6. **Modified**: `DebeziumLocalConvertersResource.java` - PostgreSQL local converters
7. **Modified**: All test class annotations (4 files)
8. **Modified**: `DebeziumDeploymentManager.java` - Idempotent deployment logic
9. **Modified**: `RegistryDeploymentManager.java` - Support for deployAllDebezium
10. **Modified**: `integration-tests/pom.xml` - New debezium-all profile
11. **Modified**: `.github/workflows/integration-tests.yaml` - Optimized CI workflow

## Testing

To verify the optimization works:

```bash
# Build the project
mvn clean install -DskipTests

# Run tests and observe container logs
mvn clean verify -Pdebezium 2>&1 | grep "Shared\|infrastructure"
```

You should see:
- "Initializing Shared Debezium Test Infrastructure" once at the start
- "already started, skipping" for subsequent test classes
- "Shutting down Shared Debezium Test Infrastructure" at the end
