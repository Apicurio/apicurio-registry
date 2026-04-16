# Apicurio Registry - GitOps Storage Examples

This directory contains examples for running Apicurio Registry in **GitOps mode**, where
registry data (schemas, artifacts, groups, rules) is loaded from a Git repository.
The registry is read-only — all changes are made by modifying files in the Git repository.

## Examples

| Example | Description | Sidecar | Security |
|---------|-------------|---------|----------|
| [Local volume](#local-volume) | Git repo cloned locally, no sidecar | No | N/A |
| [Pull HTTPS](#pull-https) | Sidecar pulls from a public repo over HTTPS | Yes | `dev` |
| [Pull SSH](#pull-ssh) | Sidecar pulls from a private repo over SSH | Yes | `default` |
| [Multi-repo HTTPS pull](#multi-repo-pull-https) | Sidecar pulls two branches (two teams) over HTTPS | Yes | `dev` |
| [Push](#push) | Sidecar accepts `git push` over SSH | Yes | `dev` |

Each example has a `docker-compose.yaml` for the full stack (registry + sidecar + UI).
Pull examples also have a `docker-compose-dev.yaml` for use with `mvn quarkus:dev`.

See the comments at the top of each compose file for setup and run instructions.

## Local Volume

The simplest setup — the example repository is cloned locally and mounted into the
registry container. No sidecar needed. Good for trying out GitOps mode and experimenting
with changes.

```bash
cd examples/gitops
docker compose up
```

To run as the `staging` registry (includes experimental schemas):

```bash
APICURIO_POLLING_STORAGE_ID=staging docker compose up
```

## Pull HTTPS

Uses the GitOps sidecar to pull from a public Git repository over HTTPS.
The sidecar clones the repo and periodically fetches updates.

```bash
cd examples/gitops/pull-https
docker compose up
```

See `pull-https/docker-compose.yaml` for fork/URL configuration options.

## Pull SSH

Uses the GitOps sidecar to pull from a private Git repository over SSH.
Requires an SSH deploy key.

**Quick setup:**

1. Generate a key: `ssh-keygen -t ed25519 -f pull-ssh/secrets/id_ed25519 -N ""`
2. Add `pull-ssh/secrets/id_ed25519.pub` as a deploy key in your Git repository
3. Run:

```bash
cd examples/gitops/pull-ssh
APICURIO_GITOPS_REPO_URL=git@github.com:your-org/your-schemas.git docker compose up
```

See `pull-ssh/docker-compose.yaml` for full setup instructions including known_hosts.

## Multi-Repo HTTPS Pull

Aggregates schemas from two Git branches (simulating two team repositories) into a
single registry using the GitOps sidecar. Uses the
[apicurio-registry-gitops-example](https://github.com/Apicurio/apicurio-registry-gitops-example)
repository:

- **`main`** (Platform team) — registry config, common schemas, order events
- **`fulfillment`** (Fulfillment team) — shipment events, experimental schemas (staging only)

```bash
cd examples/gitops/multi-repo-pull-https
docker compose up
```

**Prod** loads: common (2) + orders (1) + fulfillment (1) = 4 artifacts.
**Staging** loads the above + experimental (1) = 5 artifacts:

```bash
APICURIO_POLLING_STORAGE_ID=staging docker compose up
```

See `multi-repo-pull-https/docker-compose.yaml` for local image build instructions.

## Push

The sidecar runs an SSH server and accepts `git push` directly. Useful for
restricted networks where outbound Git access is not available, or for CI/CD
pipelines that push schema changes to the registry.

**Quick setup:**

1. Generate a key: `ssh-keygen -t ed25519 -f push/secrets/id_ed25519 -N ""`
2. Start the stack:

```bash
cd examples/gitops/push
docker compose up
```

3. Push schemas from a local Git repository:

```bash
export GIT_SSH_COMMAND="ssh -o StrictHostKeyChecking=no -i examples/gitops/push/secrets/id_ed25519 -p 2222"
git remote add registry git@localhost:/repos/default
git push registry main
```

The registry detects the new commit on the next poll cycle and loads the data.

See `push/docker-compose.yaml` for full setup instructions.

## Endpoints

All examples expose the same endpoints:

- **Registry API:** http://localhost:8080/apis/registry/v3
- **Registry UI:** http://localhost:8888

## Sample Repository

All examples use the
[apicurio-registry-gitops-example](https://github.com/Apicurio/apicurio-registry-gitops-example)
repository:

- **`main` branch** (Platform team) — registry configs (prod/staging), common schemas (address, money), order events
- **`fulfillment` branch** (Fulfillment team) — shipment events, experimental schemas (staging only)

Single-repo examples use the `main` branch alone. The multi-repo example aggregates both branches.

See the [example repository README](https://github.com/Apicurio/apicurio-registry-gitops-example)
for the full scenario description and directory layout.

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
createdOn: "2024-03-04"
```

The `registryIds` field lists which Registry instances should load this group.
If omitted or empty, the group is loaded by all registry instances.

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
versions:
  - version: "1.0.0"
    state: ENABLED
    content: ./path/to/schema.avsc
```

### Timestamps

The `createdOn` and `modifiedOn` fields are optional on groups, artifacts, and versions.
When omitted, the Git commit time is used as a fallback.

Supported formats: `2024-03-04`, `2024-03-04T10:30:00Z`, `2024-03-04T10:30:00+01:00`,
`2024-03-04T10:30:00` (assumed UTC), or unix milliseconds (`1709510400000`).

### Supported Content Types

Content files are detected by file extension:
- `.avsc`, `.avro` — Apache Avro
- `.json` — JSON Schema
- `.yaml`, `.yml` — OpenAPI / AsyncAPI
- `.proto` — Protocol Buffers
- `.graphql` — GraphQL
- `.xml`, `.xsd`, `.wsdl` — XML Schema / WSDL

## Related Documentation

- [GitOps Storage Overview](../../app/src/main/java/io/apicurio/registry/storage/impl/gitops/README.md) — architecture, configuration properties, management API, error handling
- [GitOps Sync Container](../../distro/gitops/README.md) — sidecar image configuration, security levels, threat model, build instructions
- [Example Repository](https://github.com/Apicurio/apicurio-registry-gitops-example) — sample multi-registry data
- [GitOps Design Epic](https://github.com/Apicurio/apicurio-registry/issues/7480) — design document and implementation plan
