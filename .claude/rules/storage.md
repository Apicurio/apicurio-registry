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
