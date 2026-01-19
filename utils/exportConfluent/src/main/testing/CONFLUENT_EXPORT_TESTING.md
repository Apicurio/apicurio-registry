# Confluent Schema Registry Export Testing

This document describes how to test the Confluent Schema Registry export functionality.

## Setup

### 1. Start Confluent Schema Registry

Start the Confluent Schema Registry using Docker Compose:

```bash
docker-compose -f docker-compose-confluent.yaml up -d
```

This will start:
- Zookeeper (port 2181)
- Kafka (port 9092)
- Confluent Schema Registry (port 8081)

Check that the Schema Registry is running:

```bash
curl http://localhost:8081/subjects
```

You should get an empty array: `[]`

### 2. Populate with Test Data

Run the helper script to populate the registry with test schemas:

```bash
./populate-confluent-registry.sh
```

This will register:
- 10 AVRO schemas (test-subject-1 through test-subject-10)
- 1 JSON schema (test-json-schema)
- 1 Protobuf schema (test-proto-schema)

And configure:
- Subject-level compatibility for test-subject-1 (BACKWARD)
- Global compatibility (BACKWARD)

Verify the schemas were registered:

```bash
curl http://localhost:8081/subjects | jq '.'
```

### 3. Export Data

You can export using either the v2 or v3 format:

#### Option A: Export using v2 format (original, has bugs)

```bash
cd utils/exportConfluent/src/main/testing
./export-v2.sh http://localhost:8081/
```

#### Option B: Export using v3 format (new, fixes all bugs)

```bash
cd utils/exportConfluent/src/main/testing
./export.sh http://localhost:8081/
```

You can also specify a custom output file:

```bash
./export.sh http://localhost:8081/ my-export.zip
```

Or run the export utility directly:

```bash
java -jar apicurio-registry-utils-exportConfluent-*-runner.jar http://localhost:8081/ --output my-export.zip
```

Available command-line options:
- `--output` or `-o`: Specify output file path (default: confluent-schema-registry-export.zip)
- `--insecure`: Skip SSL certificate validation
- `--client-props`: Additional client properties (key1=value1 key2=value2 ...)

Both scripts will:
1. Build the export utility if needed
2. Run the export
3. Create the export zip file in the current directory

### 4. Inspect the Export

Unzip and inspect the export to see the v2 format:

```bash
unzip -l confluent-schema-registry-export.zip
```

You should see:
- manifest-*.Manifest.json
- content/*.Content.json and *.Content.data files
- groups/default/artifacts/*/versions/*.ArtifactVersion.json files
- rules/*.GlobalRule.json files

## Known Issues with Current v2 Export

The current export utility has the following bugs:

1. **globalId is always -1**: All versions have the same globalId, causing import failures
2. **isLatest is always false**: Should be true for the latest version of each artifact
3. **versionId is always 0**: Should be 1 for the first version (or match the version number)

These issues cause:
- Only one artifact version to be successfully imported (the last one alphabetically)
- Version numbering mismatch in the ccompat API (returns version 0 instead of 1)

## v3 Export Utility (NEW)

The new v3 export utility (`ExportV3.java`) fixes all the bugs in the v2 exporter:

### Fixes Implemented:

1. ✅ **Unique GlobalIds**: Uses sequential counter (1, 2, 3...) instead of hardcoded -1
2. ✅ **Correct VersionOrder**: Sets versionOrder to match Confluent version number
3. ✅ **Proper Branch Structure**: Creates "latest" branch with systemDefined=true containing all versions
4. ✅ **Content Deduplication**: Reuses contentId for identical schemas
5. ✅ **v3 Entity Structure**: Exports proper v3 format with all required entities

### Entity Export Order:

1. ManifestEntity (version 3.0)
2. ContentEntity (with contentType field)
3. ArtifactEntity (new in v3)
4. ArtifactVersionEntity (with versionOrder)
5. BranchEntity (with systemDefined=true)
6. ArtifactRuleEntity
7. GlobalRuleEntity

### Testing

After implementing the new utility:

1. Export data from Confluent Schema Registry
2. Import into Apicurio Registry v3
3. Verify all artifacts are imported
4. Verify version numbering is consistent between Core API and ccompat API
5. Run the VersionNumberingTest to ensure the issue is fixed

## Cleanup

Stop the Confluent services:

```bash
docker-compose -f docker-compose-confluent.yaml down -v
```

The `-v` flag removes the volumes to ensure a clean state next time.