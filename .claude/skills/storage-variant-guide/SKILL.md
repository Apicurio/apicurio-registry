---
name: storage-variant-guide
description: Guide for implementing features across all storage variants. Use when
  the user is adding or modifying storage layer functionality.
allowed-tools: Read, Grep, Glob
---
# Storage Variant Implementation Guide

When implementing a feature that touches the storage layer:

1. **Start with SQL** (`app/src/.../storage/impl/sql/`) — this is the canonical implementation
2. **Adapt for KafkaSQL** (`app/src/.../storage/impl/kafkasql/`) — uses a Kafka journal pattern;
   state changes are serialized as Kafka messages and replayed
3. **Consider GitOps** (`app/src/.../storage/impl/gitops/`) — file-based, git-backed
4. **Consider KubernetesOps** (`app/src/.../storage/impl/kubernetesops/`) — ConfigMap-based
5. **Update RegistryStorage interface** if adding new operations
6. **Update decorators** (`storage/decorator/`) if the feature needs cross-cutting behavior
7. **DTOs must be serializable** — KafkaSQL journals serialize them
8. **Verify all four variants** for shared storage changes (SQL, KafkaSQL, GitOps,
   KubernetesOps); use the supported checks in `DEVELOPING.md` and report coverage gaps

## Verification evidence

Apply `AGENT-VERIFICATION.md`, reusing any supplied mode and criteria. Storage
semantics and migrations require Focused review. Check persisted state and failure
behavior, not just a successful return value. Map each criterion to the affected
variants and record exact commands/results per variant; a SQL pass does not establish
KafkaSQL, GitOps, or KubernetesOps correctness. Include replay or eventual-consistency
checks where relevant. Identify transaction, migration, and ordering decisions for
human inspection. If execution is unavailable, mark checks unexecuted.
Use `/verify-change` for the complete workflow.
