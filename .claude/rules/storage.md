---
paths:
  - "app/src/main/java/**/storage/**/*.java"
---
# Storage Layer Patterns

## Architecture
- `RegistryStorage` is the core interface (`app/src/.../storage/RegistryStorage.java`)
- Implementations in `app/src/.../storage/impl/`:
  - `sql/` — Primary. PostgreSQL via JDBC. Canonical implementation.
  - `kafkasql/` — Kafka as journal, SQL as snapshot store. Replicates via Kafka topics.
  - `gitops/` — Git repository as backing store. File-based.
  - `kubernetesops/` — Kubernetes ConfigMaps as backing store.
- Selected at runtime via `APICURIO_STORAGE_KIND` environment variable

## Guidelines
- Always implement new features in `sql/` first, then adapt to other variants
- KafkaSQL uses a journal pattern — state changes are Kafka messages replayed to SQL
- Decorators (`storage/decorator/`) wrap storage for cross-cutting concerns (metrics, auth)
- DTOs in `storage/dto/` — keep them serializable for KafkaSQL journal
- Storage events (`StorageEvent`, `StorageEventType`) fire on state changes

## Database Upgrade Scripts
- Schema changes need a new version: bump `sql/db-version`, add `sql/upgrades/<N>/<dialect>.upgrade.ddl`
  for all four dialects (each ending in `UPDATE apicurio SET propValue = <N> ...`), and update the
  `db_version` insert in the four base DDLs. `SqlSchemaConsistencyTest` checks this.
- Never change an existing upgrade script. A database never re-runs a version it has reached, so the
  change is silently skipped. If another PR took your number, move to the next free one (#10141).
- Maintenance branches (`3.3.x`) never get upgrade scripts or `db-version` changes: their sequence is
  frozen once `main` moves past it. Backport the code fix only, and put data repairs in `main`'s next
  upgrade script.
- PR validation enforces both rules; a maintainer can accept an exception with the
  `db-upgrade/change-approved` label and `/retry`.
