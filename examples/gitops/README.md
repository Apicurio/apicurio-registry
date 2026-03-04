# Apicurio Registry - GitOps Storage Example

This example demonstrates Apicurio Registry running in **GitOps mode**, where registry data (schemas, artifacts, groups, rules) is loaded from a Git repository. The registry is read-only - all changes are made by modifying files in the Git repository.

## Sample Repository

The `example-repo/` directory showcases a **multi-registry setup** where a single repository serves two Registry instances (`prod` and `staging`) with different configurations:

```
example-repo/
├── config/
│   ├── prod.registry.yaml              # prod: strict rules (VALIDITY + COMPATIBILITY)
│   └── staging.registry.yaml           # staging: no global rules (relaxed)
├── payments/
│   ├── payments.registry.yaml          # group: loaded by both prod and staging
│   ├── order-created.registry.yaml     # artifact: loaded by both prod and staging
│   ├── order-created-v1.avsc
│   └── order-created-v2.avsc
├── common/
│   ├── common.registry.yaml            # group: loaded by both prod and staging
│   ├── address.registry.yaml           # artifact: loaded by both prod and staging
│   └── address.json
└── experimental/
    ├── experimental.registry.yaml      # group: loaded by staging ONLY
    ├── user-activity.registry.yaml     # artifact: loaded by staging ONLY
    └── user-activity.avsc
```

This demonstrates:
- **Two registry configurations** with different global rules (strict prod vs relaxed staging)
- **Shared groups and artifacts** loaded by both registries (`registryIds: [prod, staging]`)
- **Staging-only experimental schemas** not visible in production (`registryIds: [staging]`)
- **One-to-many** - one repo serves multiple registries (multi-repo support is planned for a future phase)

### Data Format

Registry metadata files use the `*.registry.yaml` extension and a `$type` discriminator field:

- `$type: registry-v0` - Registry configuration (global rules, settings) scoped by `registryId`
- `$type: group-v0` - Group definition, scoped by `registryIds`
- `$type: artifact-v0` - Artifact with inline versions, scoped by `registryIds`

Content files (the actual schemas) are plain files (`.avsc`, `.json`, `.proto`, etc.) referenced from artifact metadata via relative paths.

## Running with Docker Compose

Start the full stack (Registry + UI + sample Git repo). By default, runs as the `prod` registry:

```bash
cd examples/gitops
docker compose up
```

To run as the `staging` registry instead (includes experimental schemas):

```bash
cd examples/gitops
APICURIO_POLLING_STORAGE_ID=staging docker compose up
```

**Endpoints:**
- Registry API: http://localhost:8080/apis/registry/v3
- Registry UI: http://localhost:8888

The sample repository is initialized as a Git repo in a Docker volume and mounted read-only into the Registry container.

## Running with quarkus:dev

For development, run Registry locally with Quarkus dev mode and only use Docker for the UI.

**Terminal 1 - Start Registry (as prod):**

```bash
cd app
mvn quarkus:dev \
  -Dapicurio.storage.kind=gitops \
  -Dapicurio.features.experimental.enabled=true \
  -Dapicurio.polling-storage.id=prod \
  -Dapicurio.gitops.workspace=$(pwd)/../examples/gitops \
  -Dapicurio.gitops.repo.dir=example-repo
```

Or as staging (includes experimental schemas):

```bash
cd app
mvn quarkus:dev \
  -Dapicurio.storage.kind=gitops \
  -Dapicurio.features.experimental.enabled=true \
  -Dapicurio.polling-storage.id=staging \
  -Dapicurio.gitops.workspace=$(pwd)/../examples/gitops \
  -Dapicurio.gitops.repo.dir=example-repo
```

**Terminal 2 - Start UI (optional):**

```bash
cd examples/gitops
docker compose -f docker-compose-dev.yaml up
```

**Endpoints:**
- Registry API: http://localhost:8080/apis/registry/v3
- Registry UI: http://localhost:8888

### Making Changes

To observe the GitOps reload behavior:

1. Modify a file in `example-repo/` (e.g., add a new version to an artifact)
2. If using `quarkus:dev`, changes are detected automatically from the local directory
3. If using Docker Compose, you need to commit changes to the Git repo in the volume

### Experimenting with Multi-Registry

Try switching between `prod` and `staging` to see different data:
- **prod** loads: `payments` group, `common` group (4 artifacts total)
- **staging** loads: `payments` group, `common` group, `experimental` group (5 artifacts total, including the draft `user-activity`)

## Data Format Reference

### Registry Configuration (`*.registry.yaml`)

```yaml
$type: registry-v0
registryId: my-registry
globalRules:
  - ruleType: VALIDITY
    config: FULL
properties: []
```

The `registryId` must match the Registry instance's `apicurio.polling-storage.id` configuration.

### Group Definition (`*.registry.yaml`)

```yaml
$type: group-v0
registryIds: [my-registry]
groupId: my-group
description: Description of the group
labels:
  key: value
```

The `registryIds` field lists which Registry instances should load this group. If omitted, the group is loaded by any registry.

### Artifact Definition (`*.registry.yaml`)

```yaml
$type: artifact-v0
registryIds: [my-registry]
groupId: my-group
artifactId: my-artifact
artifactType: AVRO
name: Human-readable name
description: Description of the artifact
labels:
  key: value
rules:
  - ruleType: COMPATIBILITY
    config: BACKWARD
versions:
  - version: "1.0.0"
    state: ENABLED
    description: Version description
    content: ./path/to/schema.avsc
  - version: "2.0.0"
    state: ENABLED
    content: ./path/to/schema-v2.avsc
```

The order of versions defines the `latest` branch - the last entry is what `latest` resolves to.

### Supported Content Types

Content files are detected by file extension:
- `.avsc`, `.avro` - Apache Avro
- `.json` - JSON Schema
- `.yaml`, `.yml` - OpenAPI / AsyncAPI (YAML format)
- `.proto` - Protocol Buffers
- `.graphql` - GraphQL
- `.xml`, `.xsd`, `.wsdl` - XML Schema / WSDL
