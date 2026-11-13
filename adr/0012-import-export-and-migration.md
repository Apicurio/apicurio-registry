# ADR-0012: Import, Export and the v2 to v3 Migration

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-13.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11115](https://github.com/Apicurio/apicurio-registry/issues/11115),
  [#11116](https://github.com/Apicurio/apicurio-registry/issues/11116).
- **Scope:** The export archive, the import API and its options, how a 1.x/2.x archive is upgraded to
  the v3 model, how IDs are kept, how 3.x protects itself from 2.x KafkaSQL data, and the Confluent
  Schema Registry export. Not covered: client and configuration migration (see the migration guide),
  and storage-level replication ([ADR-0010](0010-storage-architecture.md)).

## Context

3.x changed the data model (artifact and version metadata separated, branches, labels as a map,
groups as entities) and both persistent formats: the SQL schema and the KafkaSQL journal. Reading 2.x
storage in place would mean carrying two models in every storage variant. Users still need to move
everything, and keep it working: Kafka messages carry the globalId or contentId they were serialized
with, so a migration that renumbers breaks every consumer of existing topics. The same need, moving
the full state of a registry, exists within 3.x for backup, restore, and moving between storage
variants.

## Decision

1. **One archive format for every move:** a ZIP of JSON entities (content, groups, artifacts,
   versions, branches, rules, comments, contract rules) with a manifest naming the exporting server
   and the archive version. `GET /admin/export` writes it, on v2 and v3; `POST /admin/import` reads it.
2. **No in-place upgrade.** 3.x does not read 2.x databases or journal topics. A 2.x registry is
   migrated by exporting it and importing the archive into an empty 3.x.
3. **The import upgrades on the fly:** an archive whose manifest `exportVersion` starts with `1` or
   `2` goes through the upgrader (`importing/v2/SqlDataUpgrader`), which maps each v2 entity to v3 as
   it is imported; `3` goes through the v3 importer. Any other version is a `400`.
4. **IDs are kept by default** (`X-Registry-Preserve-GlobalId`, `X-Registry-Preserve-ContentId`, both
   `true` unless configured otherwise), so serialized Kafka payloads keep resolving. When they are not
   kept, new IDs are allocated and references (which are by coordinates) still resolve.
5. **An import requires an empty registry** by default (`requireEmptyRegistry`, `409` otherwise), and
   never replaces data.
6. **The same implementation for every writable storage:** SQL and KafkaSQL both run the importers
   through the storage API (KafkaSQL as journal messages); GitOps and KubernetesOps are read-only and
   refuse imports.
7. **3.x refuses a 2.x KafkaSQL topic:** at startup it checks the journal topic's configuration (no
   compaction, no retention) and scans its last records; v2 records stop the startup with an
   explanation.
8. **Confluent Schema Registry** is migrated the same way: `utils/exportConfluent` reads a Confluent
   registry through its API and writes a v3 archive, with Confluent's schema IDs as contentIds.

### Why export and import rather than an in-place upgrade

An in-place upgrade would need a migration for every storage variant and SQL dialect, a reader for
the v2 journal format in KafkaSQL, and would make a failed upgrade destructive. Export and import
reuse one code path, leave the 2.x registry untouched (it can keep serving until cut-over), and are
the same tool users need for backup and restore anyway. The cost is a copy of the data and a
cut-over.

## Model

### v2 to v3 mapping

| v2 | v3 |
|---|---|
| `null` group | group `default` |
| Artifact metadata | Artifact metadata taken from the latest version; each version keeps its own |
| Labels (list) and properties (map) | Labels (map): each v2 label becomes a key with a `null` value |
| Version order | `latest` branch, created for every artifact, with the versions in order |
| `DISABLED`, `DEPRECATED` | Same states; `latest` resolution still skips `DISABLED` |
| Content hash | Recomputed with the current algorithm; canonical hash deferred when references are not yet imported |
| globalId, contentId | Kept, or remapped (see Decision 4) |

### Guarantees

| | Behaviour |
|---|---|
| IDs | Kept by default; kept IDs keep payloads resolvable |
| Target | Empty, unless `requireEmptyRegistry=false` |
| Atomicity | None: entities are imported one by one; a failing entity is logged and skipped |
| Startup import | `apicurio.import.url`: imported once on an empty registry; readiness waits for it |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.import.preserveGlobalId` | `true` | Default for `X-Registry-Preserve-GlobalId` |
| `apicurio.import.preserveContentId` | `true` | Default for `X-Registry-Preserve-ContentId` |
| `apicurio.import.requireEmptyRegistry` | `true` | Default for `requireEmptyRegistry` |
| `apicurio.import.url` | | Archive to import at startup |
| `apicurio.import.work-dir` | | Where the archive is unpacked |
| `apicurio.import.zip.max-entry-size`, `max-total-size`, `max-entry-count` | 10 MiB, 100 MiB, 10000 | Limits on the uploaded archive |
| `apicurio.kafkasql.topic-configuration-verification-override-enabled` | `false` | Allows retention on the journal topic (with snapshots) |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| REST | [`AdminResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/AdminResourceImpl.java) (`importData`, `isUpgradeRequired`), [`DataExporter`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/shared/DataExporter.java) | Unpacking, limits, choosing import or upgrade; export |
| Archive | [`utils/importexport`](../utils/importexport/src/main/java/io/apicurio/registry/utils/impexp/) | Entities (v2 and v3), reader, manifest |
| Upgrade | [`importing/v2/SqlDataUpgrader`](../app/src/main/java/io/apicurio/registry/storage/importing/v2/SqlDataUpgrader.java) | v2 entities to v3, ID mapping |
| Import | [`importing/v3/SqlDataImporter`](../app/src/main/java/io/apicurio/registry/storage/importing/v3/SqlDataImporter.java) | v3 entities, ID mapping |
| Startup import | [`ImportLifecycleBean`](../app/src/main/java/io/apicurio/registry/ImportLifecycleBean.java) | `apicurio.import.url` |
| KafkaSQL guard | [`KafkaAdminUtil`](../app/src/main/java/io/apicurio/registry/storage/impl/util/KafkaAdminUtil.java), `kafkasql/v2compat` | Topic configuration and v2 record detection |
| Confluent | [`utils/exportConfluent`](../utils/exportConfluent/) | Confluent registry to archive |
| Scripts | [`docs/scripts`](../docs/scripts/) | Documented export, import and verification scripts |

## Limitations and open work

1. An import is not atomic, and entities that fail are only logged: the response is still `204`.
2. With `X-Registry-Preserve-ContentId: false`, content that already exists in the target is linked
   to the wrong versions ([#11115](https://github.com/Apicurio/apicurio-registry/issues/11115)).
3. The v2 API on 3.x serves a `DISABLED` version's content by globalId; 2.x did not
   ([#11116](https://github.com/Apicurio/apicurio-registry/issues/11116)).
4. The KafkaSQL guard scans the last 500 records of the topic only.
5. The Confluent exporter must be built from source.
6. Imports produce no registry events ([#11109](https://github.com/Apicurio/apicurio-registry/issues/11109)).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| In-place upgrade of 2.x storage | Not selected: per-variant migrations, destructive on failure |
| Renumber IDs by default | Not selected: breaks every serialized Kafka payload |
| Merge into a non-empty registry by default | Not selected: ID collisions; opt-in with `requireEmptyRegistry=false` |
| A separate format for backups | Not selected: one format serves migration, backup and restore |

## Consequences

- **Benefits:** one tool for migration, backup and restore; IDs and payloads survive; the 2.x registry
  is untouched until cut-over.
- **Costs:** a copy of the data and a cut-over; no atomicity; failures are only visible in the log.

## Example and verification

- **Example:** [`examples/v2-to-v3-migration`](../examples/v2-to-v3-migration/): `./demo.sh` seeds a
  2.6 server, migrates it with the documented scripts, and checks every version's IDs, state,
  content and references through the v3 and v2 APIs, the rules and metadata, the import guards, a
  renumbered import, a 3.x backup and restore, and that 3.x refuses a 2.6 KafkaSQL topic. CI runs it.
- **User documentation:**
  [`assembly-migrating-registry-v2-v3.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-migrating-registry-v2-v3.adoc).

| Concern | Tests to read |
|---|---|
| v3 import and export | `ImportExportTest`, `ProtobufExportTest`, `ContractRuleImportExportTest` (app) |
| Upgrade of 2.x archives | `DataUpgradeTest`, `SimpleDataUpgradeTest` (app) |
| Confluent archive | `ConfluentDataMigrationTest` (app) |
| Startup import | `ImportLifecycleBeanTest` (app) |

## History

| Milestone | Result |
|---|---|
| 3.0 | New storage formats; import with upgrade from 2.x archives |
| This ADR | First written record, with a verified example |
