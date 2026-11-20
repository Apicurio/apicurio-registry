# ADR-0015: Search and the Elasticsearch Index

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-16.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11170](https://github.com/Apicurio/apicurio-registry/issues/11170).
- **Scope:** How the registry searches: metadata search in the primary storage, and the optional
  Elasticsearch index for content and structure search. It covers which queries go where, how the
  index is kept up to date, and how authorization filters results. Not covered: the ARD and agent
  discovery endpoints ([ADR-0004](0004-ai-catalog-ard-convergence.md)), which use metadata search.

## Context

Users search by metadata (group, name, labels, state, type) and, increasingly, by what artifacts
contain: every OpenAPI document that mentions "customer", every schema that defines `Address`.
Metadata lives in the registry's SQL storage (on every variant, see
[ADR-0010](0010-storage-architecture.md)), where indexed queries are fast and exact, and where
authorization is applied before pagination. Content search over raw documents is a full-text problem
that SQL handles poorly, and structural search needs each type's content parsed into elements. A
search engine fits, but becomes a second copy of the data that must not change what users may see,
and whose lag must be explicit.

## Decision

1. **Metadata search stays in the primary storage.** Every query without a `content` or `structure`
   filter goes to SQL, on every storage variant, with or without the index. Nothing routes there
   "for speed", so enabling the index never changes metadata results.
2. **Content and structure search need the Elasticsearch index** (`apicurio.search.index.enabled`,
   experimental). Queries with those filters go only to the index, with **no fallback**: when the
   index is disabled or not ready, or the query is combined with a content hash filter the index
   cannot evaluate, they fail with 400; when Elasticsearch fails during the query, with 500. Never
   with a different result set.
3. **The index is a secondary, eventually consistent copy.** A storage decorator
   (`SearchIndexEventDecorator`) fires an event for each version change (created, metadata or state
   changed, deleted, artifact or group deleted); a background worker applies it, so ordinary writes
   are not slowed down and do not fail when the index does. Imports and upgrades are the exception:
   they rebuild the whole index synchronously before returning (rebuild failures are logged, not
   returned).
4. **Startup builds a missing index.** When the index is empty, or its mapping version is outdated
   (recreated), one replica (an Elasticsearch create-only lock document) streams every version from
   storage in bulk. A replica becomes ready when its own startup attempt has finished, been skipped
   because another replica holds the lock, or failed: a ready replica can still serve results from an
   incomplete index.
5. **Authorization is applied inside the query, the same way as SQL.** Per-resource rules
   ([ADR-0005](0005-per-resource-authorization.md)) are decomposed into the same name and prefix
   clauses (`AuthorizationNames`) and added to the Elasticsearch query before paging. The index does
   not store ownership (it would go stale on ownership transfers), so the artifacts the caller owns
   are resolved from primary storage and passed as names, up to 1000.
6. **Structure comes from the types.** Each artifact type contributes a structured content
   extractor (OpenAPI schemas and operations, Avro records and fields, Protobuf messages, and so on),
   indexed as `type:kind:name`.

### Why no fallback for content and structure search

A metadata-only fallback would silently answer a different question. Failing loudly makes the
dependency visible; metadata search, which users rely on, never depends on the index.

### Why events from a decorator

Every write path goes through the storage API, so one decorator sees every change without each
storage variant knowing about the index; the same decorator chain carries read-only mode and events
([ADR-0011](0011-registry-events.md)).

## Model

| Query | Goes to | Without the index |
|---|---|---|
| Metadata filters only | Primary storage (SQL) | Same |
| `content` and/or `structure` (+ metadata) | Elasticsearch (500 if it fails) | 400 |
| `content`/`structure` with `contentHash`/`canonicalHash` | (refused) | 400 |

| Storage | Index kept up to date |
|---|---|
| SQL (any database), KafkaSQL | Yes, by the replica that handles each write; all replicas share the index |
| GitOps, KubernetesOps | **No**: data is loaded after the startup rebuild and replaced without storage writes ([#11170](https://github.com/Apicurio/apicurio-registry/issues/11170)) |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.search.index.enabled` | `false` | The index (requires `apicurio.features.experimental.enabled`) |
| `apicurio.search.index.elasticsearch.index-name` | `apicurio-registry` | Index name |
| `apicurio.search.index.elasticsearch.number-of-shards`, `number-of-replicas` | 1, 1 | Index settings at creation |
| `apicurio.search.index.content.max-size` | 1048576 | Characters of content indexed per version (structure extraction sees all of it) |
| `quarkus.elasticsearch.hosts`, `username`, `password` | | The Elasticsearch connection |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Routing | [`ElasticsearchSearchDecorator`](../app/src/main/java/io/apicurio/registry/storage/decorator/ElasticsearchSearchDecorator.java) | Content/structure queries to the index; ownership resolution |
| Change events | [`SearchIndexEventDecorator`](../app/src/main/java/io/apicurio/registry/storage/decorator/SearchIndexEventDecorator.java) | One event per version change |
| Indexing | [`storage/impl/search`](../app/src/main/java/io/apicurio/registry/storage/impl/search/) | Updater, startup indexer, index manager, document builder, query service |
| Readiness | `ElasticsearchIndexReadinessCheck` | Ready once this replica's startup attempt is over (done, skipped or failed) |
| Structure | each type's `StructuredContentExtractor` | Elements indexed for structure search |

## Limitations and open work

1. GitOps and KubernetesOps data is never indexed
   ([#11170](https://github.com/Apicurio/apicurio-registry/issues/11170)).
2. The index lags writes: a successful write does not mean it is searchable yet.
3. Edits to draft version content are not reindexed until the draft is finalized.
4. Owner matches are capped at 1000 owned artifacts per query.
5. Content beyond `content.max-size` characters is not searchable.
6. A mapping change rebuilds the whole index at startup.
7. The feature is experimental.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Route all searches to Elasticsearch, fall back to SQL | Not selected: two engines answering the same query differently, and authorization implemented twice for metadata |
| Full-text search in the database (PostgreSQL FTS) | Open (#7230): one engine, but per-database implementations and no structure extraction yet |
| Index synchronously with the write | Not selected: writes would fail or slow down with the index |
| Store ownership in the index | Not selected: goes stale on ownership transfer; resolved from storage per query |

## Consequences

- **Benefits:** content and structure search; metadata search unchanged and independent; the same
  authorization in both engines; ordinary writes unaffected by the index (imports wait for a
  rebuild).
- **Costs:** an Elasticsearch cluster; eventual consistency; a gap on polling storage.

## Example and verification

- **Example:** [`examples/storage-variants`](../examples/storage-variants/): the `postgresql`,
  `kafkasql` and `gitops` profiles run Elasticsearch. `./demo.sh` checks content and structure search
  through the other replica (the index is shared), indexing of a new artifact and removal on delete,
  that metadata search does not use the index, that content search is refused without it (MySQL), and
  the GitOps gap (#11170). CI runs it.
- **User documentation:**
  [`assembly-configuring-content-search.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-configuring-content-search.adoc).

| Concern | Tests to read |
|---|---|
| Routing and errors | `ElasticsearchSearchDecoratorTest` (app) |
| Queries, authorization | `ElasticsearchSearchServiceTest`, `ElasticsearchStructureQueryTest`, `ElasticsearchAuthorizationQueryTest` (app) |
| Startup rebuild, import | `ElasticsearchStartupIndexerTest`, `ImportReindexSearchTest`, `SearchVersionsViaIndexTest` (app) |
| Structure extraction | `*StructuredContentExtractorTest` (app) |

## History

| Milestone | Result |
|---|---|
| 3.2 | Elasticsearch index, experimental |
| This ADR | First written record, with a verified example |
