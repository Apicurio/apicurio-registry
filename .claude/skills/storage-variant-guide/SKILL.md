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
8. **Test with at least SQL and KafkaSQL** integration test profiles
