# Apicurio Registry - GitOps Storage Examples

This directory contains examples for running Apicurio Registry in **GitOps mode**, where
registry data (schemas, artifacts, groups, rules) is loaded from a Git repository.
The registry is read-only — all changes are made by modifying files in the Git repository.

## Examples

| Example               | Description                                             | Sidecar | Security  |
|-----------------------|---------------------------------------------------------|---------|-----------|
| [Local volume](#local-volume)   | Git repo initialized from a local directory, no sidecar | No      | N/A       |
| [Pull HTTPS](#pull-https)     | Sidecar pulls from a public repo over HTTPS             | Yes     | `dev`     |
| [Pull SSH](#pull-ssh) | Sidecar pulls from a private repo over SSH              | Yes     | `default` |

Each example has two compose files:
- `docker-compose.yaml` — full stack (registry + sidecar + UI)
- `docker-compose-dev.yaml` — for use with `mvn quarkus:dev` (sidecar + UI only)

## Local Volume

The simplest setup — a local directory is initialized as a Git repo inside a Docker volume.
No sidecar needed. Good for trying out GitOps mode.

```bash
cd examples/gitops
docker compose up
```

To run as the `staging` registry (includes experimental schemas):

```bash
APICURIO_POLLING_STORAGE_ID=staging docker compose up
```

### Quarkus Dev Mode

```bash
# Terminal 1 — clone the example repo and start UI
cd examples/gitops
docker compose -f docker-compose-dev.yaml up

# Terminal 2 — start Registry
cd app
mvn quarkus:dev \
  -Dapicurio.storage.kind=gitops \
  -Dapicurio.features.experimental.enabled=true \
  -Dapicurio.polling-storage.id=prod \
  -Dapicurio.gitops.workspace=$(pwd)/../examples/gitops \
  -Dapicurio.gitops.repo.dir=example-repo

```

## Pull HTTPS

Uses the GitOps sidecar to pull from a public Git repository over HTTPS.
The sidecar clones the repo and periodically fetches updates.

### Setup

1. Fork [apicurio-registry-gitops-example](https://github.com/Apicurio/apicurio-registry-gitops-example)
2. Update `APICURIO_GITOPS_REPO_URL` in the compose file (or pass it via environment)
3. Run:

```bash
cd examples/gitops/pull-https

# With default example repo:
docker compose up

# With your fork:
APICURIO_GITOPS_REPO_URL=https://github.com/your-user/apicurio-registry-gitops-example.git \
  docker compose up
```

### Quarkus Dev Mode

```bash
# Terminal 1 — start sidecar + UI
cd examples/gitops/pull-https
docker compose -f docker-compose-dev.yaml up

# Terminal 2 — start Registry pointing to the sidecar's volume
cd app
mvn quarkus:dev \
  -Dapicurio.storage.kind=gitops \
  -Dapicurio.features.experimental.enabled=true \
  -Dapicurio.polling-storage.id=prod \
  -Dapicurio.gitops.workspace=/tmp/gitops-repos \
  -Dapicurio.gitops.repo.dir=example \
  -Dapicurio.gitops.repo.branch=main
```

## Pull SSH

Uses the GitOps sidecar to pull from a private Git repository over SSH.
Requires an SSH deploy key.

### Setup

1. **Generate an SSH key pair** for the sidecar:

   ```bash
   cd examples/gitops/pull-ssh
   ssh-keygen -t ed25519 -f secrets/id_ed25519 -N "" -C "apicurio-gitops-sidecar"
   ```

2. **Add the public key** as a read-only deploy key in your Git repository:
   - **GitHub:** Repository Settings > Deploy keys > Add deploy key.
     Paste the contents of `secrets/id_ed25519.pub`. Read-only access is sufficient.
   - **GitLab:** Settings > Repository > Deploy keys.

3. **(Recommended) Pre-populate known_hosts** to avoid trust-on-first-use:

   ```bash
   ssh-keyscan github.com > secrets/known_hosts
   ```

   If you skip this step, the sidecar will use TOFU (accept the host key on first
   connection and verify it on subsequent connections).

4. **Update the repository URL** and run:

   ```bash
   cd examples/gitops/pull-ssh

   APICURIO_GITOPS_REPO_URL=git@github.com:your-org/your-schemas.git \
     docker compose up
   ```

### Quarkus Dev Mode

```bash
# Terminal 1 — start sidecar + UI
cd examples/gitops/pull-ssh
APICURIO_GITOPS_REPO_URL=git@github.com:your-org/your-schemas.git \
  docker compose -f docker-compose-dev.yaml up

# Terminal 2 — start Registry
cd app
mvn quarkus:dev \
  -Dapicurio.storage.kind=gitops \
  -Dapicurio.features.experimental.enabled=true \
  -Dapicurio.polling-storage.id=prod \
  -Dapicurio.gitops.workspace=/tmp/gitops-repos \
  -Dapicurio.gitops.repo.dir=example \
  -Dapicurio.gitops.repo.branch=main
```

### Security Notes

- The `secrets/` directory is git-ignored — SSH keys are never committed.
- The sidecar copies the key internally and sets `0600` permissions.
- In `default` security mode, TOFU is used if `known_hosts` is not provided.
  For production, use `strict` mode which requires `known_hosts`.

## Endpoints

All examples expose the same endpoints:

- **Registry API:** http://localhost:8080/apis/registry/v3
- **Registry UI:** http://localhost:8888

## Sample Repository

All examples use the
[apicurio-registry-gitops-example](https://github.com/Apicurio/apicurio-registry-gitops-example)
repository, which showcases a **multi-registry setup** where a single repository serves
two Registry instances (`prod` and `staging`) with different configurations:

- **Two registry configurations** with different global rules (strict prod vs relaxed staging)
- **Shared groups and artifacts** loaded by both registries (`registryIds: [prod, staging]`)
- **Staging-only experimental schemas** not visible in production (`registryIds: [staging]`)

See the [example repository README](https://github.com/Apicurio/apicurio-registry-gitops-example)
for the full directory layout and data format details.

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
createdOn: "2024-03-04"
modifiedOn: "2024-06-15T10:30:00Z"
```

The `registryIds` field lists which Registry instances should load this group.

### Artifact Definition (`*.registry.yaml`)

```yaml
$type: artifact-v0
registryIds: [my-registry]
groupId: my-group
artifactId: my-artifact
artifactType: AVRO
name: Human-readable name
description: Description of the artifact
createdOn: "2024-03-04"
modifiedOn: "2024-06-15"
rules:
  - ruleType: COMPATIBILITY
    config: BACKWARD
versions:
  - version: "1.0.0"
    state: ENABLED
    createdOn: "2024-03-04"
    content: ./path/to/schema.avsc
  - version: "2.0.0"
    state: ENABLED
    createdOn: "2024-06-15T10:30:00Z"
    content: ./path/to/schema-v2.avsc
```

### Timestamps

The `createdOn` and `modifiedOn` fields are optional on groups, artifacts, and versions.
When omitted, the Git commit time is used as a fallback.

Supported formats:

| Format | Example |
|--------|---------|
| Date only (midnight UTC) | `2024-03-04` |
| ISO 8601 with timezone | `2024-03-04T10:30:00Z` |
| ISO 8601 with offset | `2024-03-04T11:30:00+01:00` |
| ISO 8601 without timezone (UTC) | `2024-03-04T10:30:00` |
| Unix milliseconds | `1709510400000` |

### Supported Content Types

Content files are detected by file extension:
- `.avsc`, `.avro` — Apache Avro
- `.json` — JSON Schema
- `.yaml`, `.yml` — OpenAPI / AsyncAPI
- `.proto` — Protocol Buffers
- `.graphql` — GraphQL
- `.xml`, `.xsd`, `.wsdl` — XML Schema / WSDL
