# Elasticsearch Search Index

This document describes the Elasticsearch-based search indexing feature for Apicurio Registry. When
enabled, this feature indexes all artifact version content and metadata into an external Elasticsearch
cluster, enabling full-text content search and structured element search capabilities that are not
possible with the standard SQL-based search.

> **Experimental**: This feature is experimental and requires both `apicurio.search.index.enabled` and
> `apicurio.features.experimental.enabled` to be set to `true`. The feature is available since version
> **3.2.0**.


## Table of Contents

- [Overview](#overview)
- [External Requirements](#external-requirements)
- [Enabling the Feature](#enabling-the-feature)
- [Configuration Reference](#configuration-reference)
- [Kubernetes Operator Deployment](#kubernetes-operator-deployment)
- [Architecture](#architecture)
- [Structured Content Extraction](#structured-content-extraction)
- [Using the Feature](#using-the-feature)
  - [REST API](#rest-api)
  - [User Interface](#user-interface)
- [Performance Considerations](#performance-considerations)
- [Limitations](#limitations)


## Overview

The search index works by maintaining a secondary index in Elasticsearch that mirrors the artifact
version data stored in the primary database. When artifacts are created, updated, or deleted, the
index is updated asynchronously via a background worker thread. On startup, if the index is empty
(e.g. first deployment with search enabled, or after an upgrade), a full bulk reindex is performed
automatically.

Key capabilities provided by the search index:

- **Full-text content search**: Search across the raw content of all artifact versions (e.g. find all
  OpenAPI specs that mention "customer")
- **Structured element search**: Search for specific structural elements within artifacts (e.g. find
  all artifacts that define a schema named "Pet" or a Protobuf message named "Order")
- **Improved search performance**: Offloads complex text search queries from the database to
  Elasticsearch


## External Requirements

### Elasticsearch

An external Elasticsearch cluster is required. The feature uses the official Elasticsearch Java client
provided by the `quarkus-elasticsearch-java-client` Quarkus extension.

**Supported versions**: Elasticsearch 7.x or 8.x.

**Deployment options**:

- **Single-node** development setup (e.g. Docker with `discovery.type=single-node`)
- **Multi-node** production cluster for high availability and scalability

**Example Docker Compose setup**:

```yaml
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
    ports:
      - "9200:9200"

  registry:
    image: quay.io/apicurio/apicurio-registry:latest
    environment:
      APICURIO_FEATURES_EXPERIMENTAL_ENABLED: "true"
      APICURIO_SEARCH_INDEX_ENABLED: "true"
      QUARKUS_ELASTICSEARCH_HOSTS: "elasticsearch:9200"
    ports:
      - "8080:8080"
    depends_on:
      - elasticsearch
```


## Enabling the Feature

Two properties must be set to enable the search index:

1. **Enable experimental features** (required gate):
   ```
   apicurio.features.experimental.enabled=true
   ```

2. **Enable the search index**:
   ```
   apicurio.search.index.enabled=true
   ```

3. **Configure the Elasticsearch connection**:
   ```
   quarkus.elasticsearch.hosts=localhost:9200
   ```

As environment variables:

```bash
export APICURIO_FEATURES_EXPERIMENTAL_ENABLED=true
export APICURIO_SEARCH_INDEX_ENABLED=true
export QUARKUS_ELASTICSEARCH_HOSTS=localhost:9200
```


## Configuration Reference

### Registry Search Properties

| Property | Default | Description |
|----------|---------|-------------|
| `apicurio.search.index.enabled` | `false` | Enable Elasticsearch search indexing (experimental). |
| `apicurio.search.index.elasticsearch.index-name` | `apicurio-registry` | Name of the Elasticsearch index to use. |
| `apicurio.search.index.elasticsearch.number-of-shards` | `1` | Number of primary shards for the index. |
| `apicurio.search.index.elasticsearch.number-of-replicas` | `1` | Number of replica shards for the index. |
| `apicurio.search.index.content.max-size` | `1048576` | Maximum content size (in characters) to index. Content exceeding this limit is truncated. Default is ~1 MB. |

### Quarkus Elasticsearch Client Properties

| Property | Default | Description |
|----------|---------|-------------|
| `quarkus.elasticsearch.hosts` | *(none)* | Elasticsearch host(s) in `host:port` format. Multiple hosts can be comma-separated. |
| `quarkus.elasticsearch.username` | *(none)* | Username for Elasticsearch authentication (optional). |
| `quarkus.elasticsearch.password` | *(none)* | Password for Elasticsearch authentication (optional). |
| `quarkus.elasticsearch.health.enabled` | `false` | Whether to enable the Quarkus Elasticsearch health check extension. |

### Prerequisite Property

| Property | Default | Description |
|----------|---------|-------------|
| `apicurio.features.experimental.enabled` | `false` | Master gate for experimental features. Must be `true` for search indexing to activate. |


## Kubernetes Operator Deployment

When deploying via the Apicurio Registry Operator, the search index can be configured through the
`ApicurioRegistry3` custom resource:

```yaml
apiVersion: registry.apicur.io/v1
kind: ApicurioRegistry3
metadata:
  name: my-registry
spec:
  deployment:
    replicas: 3

  searchIndex:
    enabled: true
    hosts: "elasticsearch-master.elastic:9200"
    indexName: "apicurio-registry"       # optional, defaults to "apicurio-registry"
    username: "elastic"                   # optional
    password:                             # optional, reference to a Kubernetes secret
      secretKeyRef:
        name: elastic-credentials
        key: password
```

The operator automatically sets the required environment variables on the registry pods, including
enabling the experimental features gate.


## Architecture

### Component Overview

The search index implementation consists of the following components:

| Component | Description |
|-----------|-------------|
| `ElasticsearchSearchConfig` | Holds all configuration properties for the search index. |
| `ElasticsearchIndexManager` | Manages the Elasticsearch index lifecycle: creation, deletion, refresh, and mapping version tracking. |
| `ElasticsearchDocumentBuilder` | Converts artifact version metadata and content into Elasticsearch documents. |
| `ElasticsearchIndexUpdater` | Observes CDI events and processes index updates asynchronously via a background worker thread. |
| `ElasticsearchStartupIndexer` | Performs a full bulk reindex on startup when the index is empty. |
| `ElasticsearchSearchService` | Translates search filters into Elasticsearch queries, executes searches, and maps results back to DTOs. |
| `SearchIndexEventDecorator` | Storage decorator that fires CDI events when artifact versions are created, updated, or deleted. |
| `ElasticsearchSearchDecorator` | Storage decorator that routes search queries to Elasticsearch when appropriate, with fallback to SQL. |
| `ElasticsearchIndexReadinessCheck` | Health check that blocks application readiness until the startup index build is complete. |

### Data Flow

**Indexing (write path)**:

1. A storage operation (create/update/delete) is intercepted by `SearchIndexEventDecorator`.
2. The decorator fires a CDI event (e.g. `VersionCreatedEvent`, `VersionDeletedEvent`).
3. `ElasticsearchIndexUpdater` observes the event and enqueues an `IndexingOperation`.
4. A dedicated background thread dequeues the operation, fetches the version content from storage,
   and indexes/deletes the document in Elasticsearch.

**Searching (read path)**:

1. A search request arrives at the REST API with filter parameters.
2. `ElasticsearchSearchDecorator` checks whether the search index is ready and can handle the
   filters.
3. If yes, the query is delegated to `ElasticsearchSearchService`, which translates the filters into
   an Elasticsearch query, executes it, and returns results.
4. If Elasticsearch is unavailable or the filters include unsupported types (`contentHash`,
   `canonicalHash`), the query falls back to the SQL-based storage implementation.
5. If the filters include index-only types (`content`, `structure`) but the index is not available,
   an error is returned.

### Startup Reindex

On application startup:

1. The `ElasticsearchStartupIndexer` observes the storage `READY` event.
2. It ensures the Elasticsearch index exists (creating it with the correct mapping if needed).
3. If the index is empty, it acquires a distributed lock (using Elasticsearch's `OpType.Create` for
   atomicity) to prevent multiple replicas from reindexing simultaneously.
4. It streams all versions from the database using `storage.forEachVersion()`, building documents
   in batches of 500 and submitting them via the Elasticsearch bulk API.
5. Automatic index refresh is disabled during bulk indexing for better throughput, then restored.
6. The application readiness health check passes once the reindex is complete.

### Mapping Version Tracking

The index stores a metadata document (`_mapping_version`) that tracks the mapping schema version.
On startup, if the existing index has an outdated mapping version, it is deleted and recreated with
the current mapping. This ensures compatibility across registry upgrades that change the index
structure.


## Structured Content Extraction

Each artifact type has a `StructuredContentExtractor` implementation that parses the artifact content
and extracts searchable structural elements. These elements are indexed in three formats:

- **Exact match** (`structure` field): `artifacttype:kind:name` (all lowercase) — for precise
  lookups
- **Text search** (`structure_text` field): `kind name` (tokenized) — for flexible text matching
- **Kind facet** (`structure_kind` field): `artifacttype:kind` (all lowercase) — for filtering by
  element type

### Supported Artifact Types and Extracted Elements

| Artifact Type | Element Kinds Extracted |
|---------------|----------------------|
| **OpenAPI** | `schema`, `path`, `operation`, `tag`, `parameter`, `security_scheme`, `server` |
| **AsyncAPI** | `channel`, `message`, `schema`, `operation`, `tag`, `server` |
| **Avro** | `name`, `field`, `type`, `enum_symbol`, `namespace` |
| **JSON Schema** | `id`, `property`, `definition`, `required` |
| **Protobuf** | `package`, `message`, `field`, `enum`, `service`, `rpc` |
| **GraphQL** | `type`, `field`, `enum`, `input`, `interface`, `union`, `directive` |
| **WSDL** | `service`, `port`, `binding`, `portType`, `operation`, `message`, `element`, `complexType`, `simpleType` |
| **XSD** | `element`, `complexType`, `simpleType`, `attribute` |
| **XML** | `element`, `namespace` |
| **Iceberg** | `name`, `column`, `partition`, `sort`, `sql` |
| **Agent Card (A2A)** | `skill`, `capability`, `inputmode`, `outputmode` |

Artifact types without a dedicated extractor (e.g. plain text, custom types) use a no-op extractor
and will only have their raw content indexed for full-text search.


## Using the Feature

### REST API

The search index adds two new query parameters to the existing version search endpoint:

**Endpoint**: `GET /apis/registry/v3/search/versions`

#### New Query Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `content` | `string` | Full-text search across artifact content. Matches are performed using Elasticsearch's `match` query with the `AND` operator, meaning all terms must appear in the content. |
| `structure` | `string` | Search by structured content elements. Supports three formats (see below). |

**Structure filter formats**:

| Format | Example | Behavior |
|--------|---------|----------|
| `type:kind:name` | `openapi:schema:Pet` | Exact match against the `structure` field. Matches only the specified artifact type, element kind, and name. |
| `kind:name` | `schema:Pet` | Text search against the `structure_text` field. Matches across all artifact types. |
| `name` | `Pet` | Text search against the `structure_text` field. Broadest match. |

These new parameters can be combined with all existing filter parameters (`groupId`, `artifactId`,
`name`, `description`, `labels`, `state`, `artifactType`, etc.).

#### Examples

Search for all versions whose content mentions "customer":

```
GET /apis/registry/v3/search/versions?content=customer
```

Find all OpenAPI artifacts that define a schema named "Order":

```
GET /apis/registry/v3/search/versions?structure=openapi:schema:order
```

Find any artifact with a schema element named "Address" (across all types):

```
GET /apis/registry/v3/search/versions?structure=schema:Address
```

Combine content and structure search with a type filter:

```
GET /apis/registry/v3/search/versions?content=payment&structure=schema:Invoice&artifactType=OPENAPI
```

#### Error Handling

If the `content` or `structure` parameters are used but the search index is not enabled, the API
returns an error indicating that the search index is required:

```
Content search requires the Elasticsearch search index, which is not available.
Enable the Elasticsearch search index to use content search.
```

#### Fallback Behavior

For queries that use only standard metadata filters (e.g. `groupId`, `name`, `labels`), the search
decorator routes the query to Elasticsearch when available but automatically falls back to SQL if
Elasticsearch encounters an error. This provides resilience without requiring any client-side
changes.

### User Interface

When the search index is enabled, the registry UI automatically displays two additional filter
options in the version search toolbar:

- **Content** — Full-text search of artifact content
- **Structure** — Search by structured content elements

These filters appear alongside the existing metadata filters (Name, Group, Labels, etc.) on the
search page. When the search index is disabled, these filter options are hidden from the UI so that
users are not presented with functionality that is unavailable.

The UI determines whether to show these filters by checking the `searchIndex` feature flag exposed
by the `/apis/registry/v3/system/uiConfig` configuration endpoint.


## Performance Considerations

- **Asynchronous indexing**: Index updates are processed on a background thread, so write operations
  (create/update/delete) are not slowed down by indexing. The search index is eventually consistent.
- **Bulk startup reindex**: The startup reindex uses batches of 500 documents via the Elasticsearch
  bulk API and disables automatic index refresh during the operation for optimal throughput.
- **Content truncation**: Content exceeding the configured `content.max-size` (default 1 MB) is
  truncated before indexing to prevent index bloat.
- **Distributed lock**: In multi-replica deployments, only one replica performs the startup reindex.
  Others detect the lock and skip reindexing.


## Limitations

- **Eventual consistency**: Because index updates are asynchronous, there is a brief delay (typically
  under one second) between a storage operation and the updated data appearing in search results.
- **Unsupported filters**: The `contentHash` and `canonicalHash` filters cannot be handled by
  Elasticsearch. Queries using these filters fall back to SQL.
- **Content truncation**: Very large artifacts may have incomplete content indexing due to the
  content size limit.
- **Index rebuild on mapping changes**: When the registry is upgraded to a version with a new index
  mapping, the entire index is deleted and rebuilt from scratch on startup. This can take time for
  large registries.
- **Experimental status**: This feature is experimental and its API/behavior may change in future
  releases.
