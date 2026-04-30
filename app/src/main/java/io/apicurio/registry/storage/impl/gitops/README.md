# GitOps Storage for Apicurio Registry

## Overview

GitOps storage enables managing Apicurio Registry data (schemas, artifacts, groups, rules) declaratively through
Git repositories. The registry operates in **read-only mode** — all changes are made by modifying files in Git, and
the registry automatically detects and loads them.

**Current Status**: GitOps storage is available as an experimental feature. The data format (`*-v0`) may change in
future releases. Enable with `apicurio.storage.kind=gitops` and `apicurio.features.experimental.enabled=true`.

### Why GitOps for a Schema Registry?

- **Git as the source of truth** — schemas and metadata are version-controlled with full audit history
- **Declarative configuration** — the desired state is declared in files, not constructed through API calls
- **Familiar workflows** — teams use branches, PRs, and code review to manage schemas
- **Reproducibility** — a registry can be rebuilt from scratch at any point from Git history
- **Zero database dependency** — data is loaded into in-memory H2 storage from Git on startup

### Comparison with KubernetesOps Storage

Apicurio Registry also provides a **KubernetesOps** storage variant (`apicurio.storage.kind=kubernetesops`) that
loads data from Kubernetes ConfigMaps instead of Git. Both share the same data format, processing pipeline, and
blue-green loading mechanism.

KubernetesOps is Kubernetes-native and works well with tools like ArgoCD or Flux that sync ConfigMaps from Git.
Choose KubernetesOps if you want Kubernetes-native tooling to manage the sync; choose GitOps if you want the
registry to read from Git directly without Kubernetes as an intermediary. However, we assume you deploy Registry to Kubernetes in both cases.

## How It Works

### One-Way Synchronization

Data flows in one direction only: **Git → Registry**. The registry serves data through its REST API but rejects
write operations. All modifications are made by committing changes to Git.

```
Git Repository  ──git pull/push──>  Shared Volume  ──JGit read──>  Registry (read-only)
                                    (sidecar manages)               (serves via REST API)
```

### Sidecar Architecture

The registry reads from a local Git repository on a mounted volume. It does **not** fetch from remote
repositories — that responsibility belongs to a sidecar container or external process (e.g.,
[git-sync](https://github.com/kubernetes/git-sync)).

```
┌─────────────────────────────────────────────────────┐
│  Kubernetes Pod                                      │
│  ┌──────────────┐        ┌────────────────────────┐  │
│  │  Sidecar      │        │  Registry Container    │  │
│  │  (git CLI)    │        │  JGit: read content    │  │
│  │  Fetches data │        │  Parse *.registry.yaml │  │
│  │  Handles auth │        │  Load into H2 database │  │
│  └──────┬────────┘        └───────────┬────────────┘  │
│         │ read/write                  │ read-only      │
│         ▼                            ▼                │
│  ┌────────────────────────────────────────────────┐   │
│  │                 Shared Volume                  │   │
│  └────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### Blue-Green Loading

The registry maintains two H2 in-memory databases. New data is loaded into the inactive database while the
active one continues serving requests. On successful load, the databases swap atomically. On failure, the swap
does not happen and the last known good data continues being served.

### Change Detection

The registry periodically reads the HEAD commit SHA from the Git object store on the volume. If it differs from
the last loaded SHA, a change is detected. **Debouncing** absorbs rapid successive pushes:

- **Quiet period** — wait until no new changes are seen for this duration before loading
- **Max wait** — force loading after this duration even if changes keep arriving

### Startup Behavior

The registry starts with an empty database and reports **not ready** (via Kubernetes readiness probe) until
the first successful data load completes. After that, it serves data normally and remains ready even if
subsequent loads fail.

## Data Format

Registry metadata files use configurable suffixes (default: `*.registry.yaml` and `*.ar.yaml`) with a `$type`
discriminator field. Content files are plain schema files referenced via relative paths.

### File Types

| `$type` | Description |
|---------|-------------|
| `registry-v0` | Registry configuration — global rules, settings, scoped by `registryId` |
| `group-v0` | Group definition, scoped by `registryIds` |
| `artifact-v0` | Artifact with inline versions, scoped by `registryIds` |
| `content-v0` | *(Optional)* Content metadata for explicit `contentId` and references |

### Multi-Registry Routing

A single repository can serve multiple registry instances:

- `Registry` has a single `registryId` — must match the instance's `apicurio.polling-storage.id`
- `Group` and `Artifact` have `registryIds` lists — an entity is loaded by all listed registries
- If `registryIds` is omitted or empty, the entity is loaded by any registry (simple setups)

### Example Repository Layout

```
my-schemas/
├── config/
│   ├── prod.registry.yaml            # registry-v0: registryId: prod
│   └── staging.registry.yaml         # registry-v0: registryId: staging
├── payments/
│   ├── payments.registry.yaml        # group-v0: registryIds: [prod, staging]
│   ├── order-created.registry.yaml   # artifact-v0: registryIds: [prod, staging]
│   ├── order-created-v1.avsc
│   └── order-created-v2.avsc
└── experimental/
    ├── experimental.registry.yaml    # group-v0: registryIds: [staging]
    ├── user-activity.registry.yaml   # artifact-v0: registryIds: [staging]
    └── user-activity.avsc
```

For complete data format reference and runnable examples, see `examples/gitops/`.

## Use Cases

### Schema Management with Code Review

Store schemas alongside application code. Schema changes go through the same PR and review process as code
changes, ensuring quality and compatibility before deployment.

### Multi-Environment Promotion

Different registry instances point to different branches of the same repository:
- `dev` branch → Development Registry
- `staging` branch → Staging Registry
- `main` branch → Production Registry

Schema promotion is done through Git merges.

### Push Model for Restricted Networks

In environments where outbound network access is restricted, an external process pushes changes to a
Git repository hosted inside the cluster. The sidecar exposes an SSH endpoint for receiving pushes.
See [`distro/gitops/README.md`](../../../../distro/gitops/README.md) for push mode configuration.

### PR Verification with CI/CD *(planned)*

Validate schema changes in CI before merging PRs. A planned dry-run endpoint or CLI tool will load
data from a branch without affecting the live registry, reporting any errors.

## Configuration

### Shared Properties (`apicurio.polling-storage.*`)

These properties are shared between GitOps and KubernetesOps storage implementations.

| Property | Default | Description |
|----------|---------|-------------|
| `apicurio.polling-storage.id` | `main` | Registry instance identifier. Only data matching this ID is loaded. Must match the `registryId` in registry configuration files. |
| `apicurio.polling-storage.deterministic-ids-enabled` | `true` | Generate `contentId` and `globalId` from content/coordinate hashes when not specified. |
| `apicurio.polling-storage.poll-period` | `PT10S` | Minimum period between polls. |
| `apicurio.polling-storage.debounce.quiet-period` | `PT30S` | Wait for no new changes before loading. Set to `PT0S` to disable. |
| `apicurio.polling-storage.debounce.max-wait-period` | `PT90S` | Force loading after this duration. Set to `PT0S` for no limit. |
| `apicurio.polling-storage.file-suffixes` | `registry,ar` | Comma-separated suffixes identifying metadata files (combined with `.yaml`, `.yml`, `.json`). |

### GitOps-Specific Properties (`apicurio.gitops.*`)

#### Single-repo (shorthand)

| Property | Default | Description |
|----------|---------|-------------|
| `apicurio.gitops.workspace` | `/repos` | Base directory where Git repositories are mounted. |
| `apicurio.gitops.repo.dir` | `default` | Directory name of the Git repository, relative to the workspace. |
| `apicurio.gitops.repo.branch` | `main` | Branch to read from. |

#### Multi-repo (indexed)

For multiple repositories, use indexed properties. If indexed repos are configured,
the single-repo shorthand properties must not be set.

| Property | Default | Description |
|----------|---------|-------------|
| `apicurio.gitops.repos.N.dir` | *(required)* | Directory name for repo N. |
| `apicurio.gitops.repos.N.branch` | `main` | Branch to read from for repo N. |
| `apicurio.gitops.repos.N.id` | *(dir name)* | Optional identifier for repo N, used in status reporting. |

Indexes must be dense (0, 1, 2, ... — no gaps). Example:

```properties
apicurio.gitops.repos.0.dir=platform
apicurio.gitops.repos.1.dir=fulfillment
apicurio.gitops.repos.1.branch=fulfillment
```

When using environment variables, use the standard underscore format:
`APICURIO_GITOPS_REPOS_0_DIR`.

**Conflict detection:** If the same `groupId:artifactId` appears in files from
different repositories, the entire load is rejected. Each artifact must be defined
in exactly one repository.

## Management API

The registry exposes management endpoints at `/apis/registry/v3/admin/gitops/` when running
in GitOps mode. These return HTTP 409 if a different storage backend is active.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/admin/gitops/status` | GET | Returns current sync state, commit SHA, load stats, and errors |
| `/admin/gitops/sync` | POST | Triggers an immediate sync (returns 204) |

The status response includes:
- `syncState` — one of `INITIALIZING`, `IDLE`, `LOADING`, `SWITCHING`, `ERROR`
- `lastSuccessfulSync` / `lastSyncAttempt` — timestamps
- `groupCount`, `artifactCount`, `versionCount` — load statistics
- `errors` — structured errors from the last failed load, each with `detail`, optional `source` (repo ID), and optional `context` (file path)
- `sources` — per-source identifiers (map of source ID → abbreviated commit SHA)

## Timestamps

Groups, artifacts, and versions support optional `createdOn` and `modifiedOn` fields.
When omitted, the Git commit time is used as a fallback.

Supported formats: ISO 8601 (`2024-03-04`, `2024-03-04T10:30:00Z`, `2024-03-04T10:30:00+01:00`),
ISO 8601 without timezone (assumed UTC), or unix milliseconds.

## Rule Enforcement

Configured rules (validity, compatibility) are enforced during loading. After all data is imported
into the inactive database, a validation pass checks artifact versions against the rules configured
in that same data set. If any rule is violated, the load is rejected and the previous data continues
being served.

**How it works:**

1. Data is loaded into the inactive database (groups, artifacts, versions, rules)
2. The validator iterates artifacts and resolves the effective rules using the standard hierarchy:
   artifact rules → group rules → global rules → default rules
3. For each artifact, it determines which versions need validation and checks them

**Why validate against the current data only:** The validator checks the loaded data against
itself — not against previously served data. This means if version 2.0 was compatible with 1.0
when it was originally added, but rules are later tightened, the validator won't retroactively
fail old versions. This design avoids the need to track rule change history across loads.

### `validatedUpTo`

The `validatedUpTo` field on an artifact controls which versions are validated:

- **Not set** (default) — only the last version is validated against its predecessor.
  This is safe for the common case of adding new versions incrementally.
- **Set to a version** (e.g., `"2.0"`) — versions up to and including that version are
  skipped. Consecutive pairs after it are validated. Use this when adding multiple versions
  at once, or after tightening rules to avoid re-validating historical data.
- **Set to the latest version** — skips all validation for this artifact. Use as an explicit
  opt-out, e.g., after a rule change that would break existing versions.

Example:

```yaml
$type: artifact-v0
groupId: orders
artifactId: order-created
artifactType: AVRO
rules:
  - ruleType: COMPATIBILITY
    config: BACKWARD
validatedUpTo: "2.0"      # v1→v2 was valid under old rules, skip re-check
versions:
  - version: "1.0"
    content: ./v1.avsc
  - version: "2.0"
    content: ./v2.avsc
  - version: "3.0"         # Only this is validated (against v2.0)
    content: ./v3.avsc
```

### Supported rules

| Rule | Supported | Notes |
|------|-----------|-------|
| VALIDITY | Yes | Validates content syntax against artifact type |
| COMPATIBILITY | Yes | Checks consecutive version pairs |
| INTEGRITY | Yes | Validates references exist via `content-v0` metadata files |

## Error Handling

If any file fails to parse or validate, the **entire load is rejected**. The blue-green swap does not happen,
and the last known good data continues being served. Errors are logged with file paths and details and
are visible via the management API status endpoint.

**Data errors** (invalid rules, missing files, parse failures, rule violations) mark the commit as
processed — the same broken commit will not be retried. A new commit that fixes the issue will trigger
a fresh load.

**Transient errors** (database issues, out-of-memory) do not mark the commit — the next poll cycle will
retry the same commit automatically.

Error categories:
- Malformed YAML/JSON syntax in metadata files
- Unrecognized `$type` values (logged as warning for forward compatibility)
- Missing required fields
- Missing content files (broken relative path references)
- Duplicate artifacts or groups
- Rule violations (validity, compatibility)

## GitOps Sync Container

A pre-built container image (`quay.io/apicurio/apicurio-registry-gitops-sync`) is available
for pulling from remote Git repositories. It runs as a sidecar alongside the registry,
managing a shared volume. See [`distro/gitops/README.md`](../../../../distro/gitops/README.md)
for configuration, security levels, and deployment details.

## Future Work

The following features are planned but not yet implemented:

- **Dry-run validation** — validate schema changes from a branch without affecting live data
- **CLI validator** — offline validation of `*.registry.yaml` files without a running registry
- **Per-file git history timestamps** — derive `createdOn`/`modifiedOn` from git log per file

For the full design document and implementation plan, see the
[GitOps design epic](https://github.com/Apicurio/apicurio-registry/issues/7480).

## Getting Started

For complete working examples with Docker Compose, see [`examples/gitops/`](../../../../examples/gitops/).

<!--

## How to update this document

When updating this document, follow these rules:

1. Keep this section hidden to avoid distracting the target audience and do not edit it.
2. This document provides information for *users* of the Registry GitOps feature *not* developers. Do not include
   implementation details, unless they are relevant for users to understand the behavior of the feature. Do not
   include source code.
3. The document should be *clear and concise*.
    - You can use ASCII art diagrams if needed, but try to keep them simple and not too many.
5. The document should include the following topics (in generally this order, but feel free to adjust as needed):
    - Short overview of GitOps and why it is important
    - Short comparison to the Kubernetesops storage. It can also be used for GitOps using ArgoCD or similar, but is Kubernetes specific.
    - General design and how it works, including the following:
        - How the Git repository is organized and what files are stored there
        - How the Registry interacts with the Git repository (e.g. how it reads/writes data, how it handles conflicts, etc.)
    - Interesting use-cases using existing or planned features (mark them appropriately), such as:
        - M:N model
        - push model in restricted networks
        - PR verification using GitHub Actions or similar, using CLI or dry run
    - Configuration properties and what they mean
6. This feature is work in progress, mention the following when appropriate:
    - The write-side of GitOps storage (meaning the repository management) is not finished yet. Add a short description about how it will work after subsequent phases are complete.
-->
