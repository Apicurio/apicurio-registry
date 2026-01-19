# Confluent Export Integration Test

## ConfluentExportIT

This is a manual integration test for the Confluent Schema Registry export utility.

### What it does:

1. **Starts Confluent Schema Registry** using Testcontainers (Kafka + Schema Registry)
2. **Starts Apicurio Registry** using Testcontainers (or connects to external instance)
3. **Populates** the registry with 5 test schemas (3 AVRO, 1 JSON, 1 Protobuf)
4. **Configures rules** (global and artifact-level compatibility rules)
5. **Exports** the data to a zip file using the Export utility
6. **Imports** the zip file into Apicurio Registry
7. **Verifies** all data was imported correctly

### Prerequisites:

- **Docker** must be running (for Testcontainers)
- No other services need to be started manually - the test handles everything!

### How to run:

#### Option 1: Fully automated (recommended)

The test will automatically start both Confluent Schema Registry and Apicurio Registry using Testcontainers:

```bash
cd utils/exportConfluent
mvn test -Dtest=ConfluentExportIT
```

This will:
- Start Kafka
- Start Confluent Schema Registry
- Start Apicurio Registry (in-memory H2 database)
- Run the complete export/import workflow
- Verify all data
- Clean up all containers

#### Option 2: Use existing Apicurio Registry

If you prefer to use an existing Apicurio Registry instance (e.g., one you're debugging):

```bash
# Start Apicurio Registry
cd ../../app
mvn quarkus:dev
```

Then in another terminal:

```bash
cd utils/exportConfluent
export USE_EXTERNAL_APICURIO=true
export APICURIO_URL=http://localhost:8080/apis/registry/v3
mvn test -Dtest=ConfluentExportIT
```

This will only start Confluent Schema Registry via Testcontainers and connect to your running Apicurio Registry instance.

### Note:

The test is disabled by default (`@Disabled` annotation) to prevent it from running during normal builds.
To enable it permanently, remove the `@Disabled` annotation from the test class.

### What gets verified:

✅ 5 artifacts imported
✅ Each artifact has version "1" with versionOrder=1
✅ GlobalIds are positive (not -1)
✅ Global compatibility rule = BACKWARD
✅ user-schema compatibility rule = FULL
✅ "latest" branch exists for all artifacts

All verifications use proper v3 format entities!