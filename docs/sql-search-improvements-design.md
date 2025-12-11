# SQL Search Functionality Improvements Design

## Executive Summary

This document provides a comprehensive design plan to address the existing limitations in the search functionality of Apicurio Registry's SQL storage implementation. The current search implementation uses basic SQL LIKE queries which have significant limitations in terms of performance, relevance ranking, and advanced query capabilities.

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Identified Limitations](#identified-limitations)
3. [Proposed Improvements](#proposed-improvements)
4. [Implementation Phases](#implementation-phases)
5. [Database-Specific Considerations](#database-specific-considerations)
6. [API Changes](#api-changes)
7. [Migration Strategy](#migration-strategy)

---

## Current State Analysis

### Overview

The current search implementation is located in `AbstractSqlRegistryStorage.java` and provides three main search methods:

1. **`searchArtifacts()`** (lines 993-1226): Searches artifacts by name, description, groupId, artifactId, labels, content/canonical hash, globalId, contentId, state, and artifact type.

2. **`searchVersions()`** (lines 1678-1846): Searches versions by groupId, artifactId, version, name, description, labels, contentHash, canonicalHash, globalId, contentId, and state.

3. **`searchGroups()`** (lines 2926-3032): Searches groups by groupId, description, and labels.

### Current Filter Types

The `SearchFilterType` enum (`app/src/main/java/io/apicurio/registry/storage/dto/SearchFilterType.java`) defines:

```java
groupId, artifactId, version, name, description, labels,
contentHash, canonicalHash, globalId, contentId, state, artifactType
```

### Current Query Implementation

The search queries are dynamically built using string concatenation with:
- **Exact matches**: `=` / `!=` operators for identifiers (groupId, artifactId, version, etc.)
- **Partial matches**: `LIKE` with `%value%` pattern for name and description
- **Wildcard support**: Limited to `*` prefix/suffix for name filter only
- **Label search**: Subquery with EXISTS clause against separate label tables
- **Negation support**: `NOT` modifier for inverting filter conditions

### Current Database Schema (from `postgresql.ddl`)

Key tables with search-relevant indexes:

```sql
-- Artifacts table
CREATE INDEX IDX_artifacts_3 ON artifacts(name);
CREATE INDEX IDX_artifacts_4 ON artifacts(description);

-- Versions table
CREATE INDEX IDX_versions_3 ON versions(name);
CREATE INDEX IDX_versions_4 ON versions(description);

-- Label tables (case-insensitive search supported)
CREATE INDEX IDX_alabels_1 ON artifact_labels(labelKey);
CREATE INDEX IDX_alabels_2 ON artifact_labels(labelValue);
CREATE INDEX IDX_vlabels_1 ON version_labels(labelKey);
CREATE INDEX IDX_vlabels_2 ON version_labels(labelValue);
CREATE INDEX IDX_glabels_1 ON group_labels(labelKey);
CREATE INDEX IDX_glabels_2 ON group_labels(labelValue);
```

---

## Identified Limitations

### 1. Performance Limitations

| Issue | Description | Impact |
|-------|-------------|--------|
| **Full table scans** | `LIKE '%term%'` patterns cannot use B-tree indexes | Severe degradation with large datasets |
| **No index optimization** | Current indexes on name/description are B-tree, not optimized for substring search | Query performance degrades linearly |
| **Correlated subqueries** | Label and content searches use EXISTS with correlated subqueries | Multiplied query cost |
| **N+1 query pattern** | Labels are fetched separately after main query results | Additional database round trips |

### 2. Feature Limitations

| Feature | Current State | User Impact |
|---------|--------------|-------------|
| **Full-text search** | Not supported | No stemming, synonym matching, or linguistic processing |
| **Relevance ranking** | Not implemented | Results are ordered by sort field only, not relevance |
| **Multi-field search** | Not supported | Users must search one field at a time |
| **Fuzzy matching** | Not supported | Typo tolerance not available |
| **Phrase search** | Not supported | Cannot search for exact phrases |
| **Boolean operators** | Limited (only AND) | No OR, NOT within query, phrase grouping |
| **Content search** | Hash-based only | Cannot search within schema content |
| **Faceted search** | Not supported | No aggregation for filter refinement |
| **Search highlighting** | Not supported | Matched terms not highlighted in results |
| **Auto-complete/suggestions** | Not implemented | No type-ahead search support |

### 3. Query Language Limitations

| Limitation | Description |
|------------|-------------|
| **No expression syntax** | Cannot combine filters with OR logic |
| **No field-specific queries** | Cannot do `name:foo AND description:bar` in single query |
| **No range queries** | Cannot search by date ranges (createdOn, modifiedOn) |
| **No regex support** | Cannot use regular expressions |
| **Inconsistent case handling** | Labels are lowercased, but name/description are not |

### 4. Scalability Concerns

| Concern | Current Behavior | At Scale Impact |
|---------|-----------------|-----------------|
| **No query result caching** | Every search hits database | High load under concurrent searches |
| **No search indexing strategy** | Relies on DB indexes only | Suboptimal for large registries |
| **Count queries** | Separate count query per search | Doubled database load |
| **No pagination optimization** | OFFSET/LIMIT only | Deep pagination inefficient |

### 5. Multi-Database Support Inconsistencies

The implementation must support PostgreSQL, H2, MySQL, and SQL Server. Current inconsistencies:
- MSSQL uses `OFFSET...FETCH` vs `LIMIT...OFFSET`
- No database-specific query optimization
- No use of database-specific full-text capabilities

---

## Proposed Improvements

### Phase 1: Query Optimization (Foundation)

#### 1.1 Optimize Existing LIKE Queries

**Approach**: Add trigram indexes for PostgreSQL and optimize query patterns.

```sql
-- PostgreSQL: Enable pg_trgm extension
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create GIN trigram indexes for substring search
CREATE INDEX IDX_artifacts_name_trgm ON artifacts USING GIN (name gin_trgm_ops);
CREATE INDEX IDX_artifacts_desc_trgm ON artifacts USING GIN (description gin_trgm_ops);
CREATE INDEX IDX_versions_name_trgm ON versions USING GIN (name gin_trgm_ops);
CREATE INDEX IDX_versions_desc_trgm ON versions USING GIN (description gin_trgm_ops);
```

**Java Implementation**:

```java
// New interface method in SqlStatements.java
public String getSubstringMatchOperator();
public boolean supportsTrigram();
```

#### 1.2 Unified Count with Window Functions

**Current**: Two separate queries (data + count)

**Proposed**: Use window functions where supported

```sql
-- PostgreSQL, MSSQL
SELECT *, COUNT(*) OVER() as total_count
FROM artifacts a
WHERE ...
ORDER BY ...
LIMIT ? OFFSET ?
```

**Fallback**: Keep current approach for H2/MySQL

#### 1.3 Label Search Optimization

**Current**: EXISTS subquery per label

**Proposed**: Use JOIN with aggregation for multiple labels

```sql
SELECT a.*, COUNT(DISTINCT l.labelKey) as matched_labels
FROM artifacts a
LEFT JOIN artifact_labels l ON a.groupId = l.groupId AND a.artifactId = l.artifactId
WHERE (l.labelKey = ? AND l.labelValue = ?) OR (l.labelKey = ? AND l.labelValue = ?)
GROUP BY a.groupId, a.artifactId
HAVING COUNT(DISTINCT l.labelKey) >= ?  -- number of required labels
```

### Phase 2: Full-Text Search (Core Enhancement)

#### 2.1 PostgreSQL Full-Text Search

**Schema Changes**:

```sql
-- Add tsvector columns for full-text search
ALTER TABLE artifacts ADD COLUMN search_vector tsvector;
ALTER TABLE versions ADD COLUMN search_vector tsvector;
ALTER TABLE groups ADD COLUMN search_vector tsvector;

-- Create GIN indexes
CREATE INDEX IDX_artifacts_search ON artifacts USING GIN(search_vector);
CREATE INDEX IDX_versions_search ON versions USING GIN(search_vector);
CREATE INDEX IDX_groups_search ON groups USING GIN(search_vector);

-- Function to update search vector
CREATE OR REPLACE FUNCTION update_artifact_search_vector() RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.name, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.description, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.artifactId, '')), 'A');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER artifact_search_vector_update
    BEFORE INSERT OR UPDATE ON artifacts
    FOR EACH ROW EXECUTE FUNCTION update_artifact_search_vector();
```

**Query Implementation**:

```java
// New SearchFilter type
public enum SearchFilterType {
    // ... existing types ...
    fullText,      // New: full-text search across multiple fields
    owner,         // New: search by owner
    createdBefore, // New: date range
    createdAfter,  // New: date range
    modifiedBefore,
    modifiedAfter
}
```

**Search Query**:

```sql
-- Full-text search with ranking
SELECT a.*, ts_rank(a.search_vector, query) AS rank
FROM artifacts a, plainto_tsquery('english', ?) query
WHERE a.search_vector @@ query
ORDER BY rank DESC, a.modifiedOn DESC
LIMIT ? OFFSET ?
```

#### 2.2 Database Abstraction Layer

Create a new abstraction for search capabilities:

```java
// New interface: SearchCapabilities.java
public interface SearchCapabilities {
    boolean supportsFullTextSearch();
    boolean supportsFuzzyMatch();
    boolean supportsRelevanceRanking();
    boolean supportsTrigram();
    FullTextSearchEngine getSearchEngine();
}

// Implementation per database
public enum FullTextSearchEngine {
    NONE,           // H2 fallback
    POSTGRESQL_FTS, // PostgreSQL tsvector
    MYSQL_FTS,      // MySQL FULLTEXT
    MSSQL_FTS       // SQL Server Full-Text
}
```

#### 2.3 MySQL Full-Text Support

```sql
-- MySQL FULLTEXT indexes
ALTER TABLE artifacts ADD FULLTEXT INDEX FT_artifacts_1 (name, description);
ALTER TABLE versions ADD FULLTEXT INDEX FT_versions_1 (name, description);
```

**Query**:
```sql
SELECT *, MATCH(name, description) AGAINST(? IN NATURAL LANGUAGE MODE) AS relevance
FROM artifacts
WHERE MATCH(name, description) AGAINST(? IN NATURAL LANGUAGE MODE)
ORDER BY relevance DESC
LIMIT ? OFFSET ?
```

#### 2.4 SQL Server Full-Text Support

```sql
-- SQL Server Full-Text Catalog
CREATE FULLTEXT CATALOG RegistrySearchCatalog AS DEFAULT;
CREATE FULLTEXT INDEX ON artifacts(name, description) KEY INDEX PK_artifacts;
CREATE FULLTEXT INDEX ON versions(name, description) KEY INDEX PK_versions;
```

### Phase 3: Advanced Search Features

#### 3.1 New Search Filter Types

```java
public enum SearchFilterType {
    // Existing
    groupId, artifactId, version, name, description, labels,
    contentHash, canonicalHash, globalId, contentId, state, artifactType,

    // New - Phase 3
    fullText,           // Unified full-text search
    owner,              // Search by owner/creator
    createdAfter,       // Date range: created after
    createdBefore,      // Date range: created before
    modifiedAfter,      // Date range: modified after
    modifiedBefore,     // Date range: modified before
    hasLabels,          // Has any labels (boolean)
    hasReferences,      // Has references (boolean)
    referencedBy,       // Referenced by artifact
    references,         // References artifact
}
```

#### 3.2 Query Expression Parser

Implement a simple query language for advanced searches:

```
// Query syntax examples:
"user service"                    // Full-text search
name:UserService                  // Field-specific exact match
name:User* AND type:AVRO          // Wildcards + boolean
labels:team:platform              // Label with key:value
created:>2024-01-01               // Date comparison
state:ENABLED OR state:DEPRECATED // OR condition
```

**Parser Implementation**:

```java
public class SearchQueryParser {
    public Set<SearchFilter> parse(String query) {
        // Tokenize and build filter set
    }
}
```

#### 3.3 Faceted Search Support

Add aggregation capabilities for filter refinement:

```java
public class ArtifactSearchResultsDto {
    // Existing fields
    private List<SearchedArtifactDto> artifacts;
    private Integer count;

    // New: Facets
    private Map<String, List<FacetValue>> facets;
}

public class FacetValue {
    private String value;
    private Long count;
}
```

**SQL Implementation**:

```sql
-- Facet query for artifact types
SELECT type, COUNT(*) as count
FROM artifacts a
WHERE ... (same filters)
GROUP BY type
ORDER BY count DESC;

-- Facet query for labels
SELECT labelKey, labelValue, COUNT(*) as count
FROM artifact_labels l
JOIN artifacts a ON l.groupId = a.groupId AND l.artifactId = a.artifactId
WHERE ... (same filters)
GROUP BY labelKey, labelValue
ORDER BY count DESC
LIMIT 20;
```

#### 3.4 Search Suggestions/Auto-complete

```java
// New REST endpoint
@GET
@Path("/search/suggestions")
public List<SearchSuggestion> getSuggestions(
    @QueryParam("prefix") String prefix,
    @QueryParam("field") String field,
    @QueryParam("limit") int limit
);
```

**Implementation**:

```sql
-- Name suggestions
SELECT DISTINCT name, COUNT(*) as freq
FROM artifacts
WHERE name ILIKE ? || '%'
GROUP BY name
ORDER BY freq DESC, name
LIMIT ?;

-- Label key suggestions
SELECT DISTINCT labelKey, COUNT(*) as freq
FROM artifact_labels
WHERE labelKey ILIKE ? || '%'
GROUP BY labelKey
ORDER BY freq DESC
LIMIT ?;
```

### Phase 4: Performance & Scalability

#### 4.1 Search Result Caching

Implement query result caching using existing infrastructure:

```java
@Inject
@ConfigProperty(name = "apicurio.search.cache.enabled", defaultValue = "false")
boolean searchCacheEnabled;

@Inject
@ConfigProperty(name = "apicurio.search.cache.ttl-seconds", defaultValue = "60")
int searchCacheTtlSeconds;
```

#### 4.2 Cursor-Based Pagination

For large result sets, implement cursor-based pagination:

```java
public class SearchCursor {
    private String lastGroupId;
    private String lastArtifactId;
    private Object lastSortValue;

    public String encode() { /* Base64 encode */ }
    public static SearchCursor decode(String cursor) { /* Decode */ }
}
```

**SQL Implementation**:

```sql
-- Cursor-based pagination (after decoding cursor)
SELECT * FROM artifacts a
WHERE (a.name, a.groupId, a.artifactId) > (?, ?, ?)
ORDER BY a.name, a.groupId, a.artifactId
LIMIT ?
```

#### 4.3 Async Search for Large Datasets

For very large result sets, implement async search:

```java
// Returns immediately with a search ID
@POST
@Path("/search/artifacts/async")
public SearchJob startAsyncSearch(SearchRequest request);

// Poll for results
@GET
@Path("/search/jobs/{jobId}")
public SearchJobStatus getSearchStatus(@PathParam("jobId") String jobId);

// Get results when ready
@GET
@Path("/search/jobs/{jobId}/results")
public ArtifactSearchResults getSearchResults(@PathParam("jobId") String jobId);
```

---

## Implementation Phases

### Phase 1: Query Optimization (2-3 weeks)

1. Add trigram extension support for PostgreSQL
2. Implement unified count with window functions
3. Optimize label search queries
4. Add database-specific query optimizations
5. Add comprehensive search tests

### Phase 2: Full-Text Search (3-4 weeks)

1. Create SearchCapabilities abstraction
2. Implement PostgreSQL full-text search
3. Implement MySQL full-text search
4. Implement SQL Server full-text search
5. Graceful fallback for H2
6. Add relevance ranking to results
7. Update REST API with fullText parameter
8. Write database migration scripts

### Phase 3: Advanced Features (4-5 weeks)

1. Add new search filter types (owner, date ranges)
2. Implement query expression parser
3. Add faceted search support
4. Implement search suggestions
5. Add result highlighting
6. Update OpenAPI specification

### Phase 4: Performance & Scalability (2-3 weeks)

1. Implement search result caching
2. Add cursor-based pagination
3. Implement async search for large datasets
4. Performance testing and tuning
5. Documentation updates

---

## Database-Specific Considerations

### PostgreSQL

**Advantages**:
- Excellent full-text search with tsvector/tsquery
- GIN indexes for trigram and full-text
- Rich query operators (@@, <->, etc.)
- Weight-based ranking

**Implementation**:
```java
public class PostgreSQLSqlStatements extends CommonSqlStatements {
    @Override
    public boolean supportsFullTextSearch() { return true; }

    @Override
    public String fullTextSearchQuery() {
        return "search_vector @@ plainto_tsquery('english', ?)";
    }

    @Override
    public String relevanceRankFunction() {
        return "ts_rank(search_vector, plainto_tsquery('english', ?))";
    }
}
```

### MySQL

**Advantages**:
- Built-in FULLTEXT indexes
- Natural language mode
- Boolean mode for advanced queries

**Limitations**:
- Minimum word length (default 4 chars)
- Stopword filtering
- Less flexible than PostgreSQL

**Implementation**:
```java
public class MySQLSqlStatements extends CommonSqlStatements {
    @Override
    public boolean supportsFullTextSearch() { return true; }

    @Override
    public String fullTextSearchQuery() {
        return "MATCH(name, description) AGAINST(? IN NATURAL LANGUAGE MODE)";
    }
}
```

### SQL Server

**Advantages**:
- Full-Text Search feature
- Semantic search capabilities
- Integration with .NET ecosystem

**Considerations**:
- Requires Full-Text Search feature enabled
- Separate catalog management

### H2 (In-Memory)

**Limitations**:
- No built-in full-text search
- Limited index types

**Fallback Strategy**:
- Continue with LIKE queries
- Apply case-insensitive comparison
- Document performance limitations

---

## API Changes

### Updated REST Endpoints

#### Search Artifacts V3

```yaml
/search/artifacts:
  get:
    parameters:
      # Existing
      - name: name
      - name: description
      - name: groupId
      - name: artifactId
      - name: labels
      - name: offset
      - name: limit
      - name: order
      - name: orderby

      # New Phase 2
      - name: q
        description: Full-text search query
        schema:
          type: string

      # New Phase 3
      - name: owner
        description: Filter by artifact owner
      - name: createdAfter
        description: Filter by creation date (ISO 8601)
        schema:
          type: string
          format: date-time
      - name: createdBefore
      - name: modifiedAfter
      - name: modifiedBefore
      - name: includeFacets
        description: Include facet aggregations
        schema:
          type: boolean
          default: false
```

#### New Endpoints

```yaml
/search/suggestions:
  get:
    summary: Get search suggestions for auto-complete
    parameters:
      - name: prefix
        required: true
      - name: field
        description: Field to suggest from (name, label, groupId)
      - name: limit
        default: 10

/search/facets:
  get:
    summary: Get available facets for current search
    parameters:
      # Same as search artifacts
```

### Updated Response Schema

```yaml
ArtifactSearchResults:
  properties:
    artifacts:
      type: array
      items:
        $ref: '#/components/schemas/SearchedArtifact'
    count:
      type: integer

    # New
    facets:
      type: object
      additionalProperties:
        type: array
        items:
          $ref: '#/components/schemas/FacetValue'

SearchedArtifact:
  properties:
    # Existing fields...

    # New
    relevanceScore:
      type: number
      description: Search relevance score (0.0-1.0)
    matchHighlights:
      type: object
      description: Field highlights showing matched terms
      additionalProperties:
        type: string
```

---

## Migration Strategy

### Database Migration Scripts

#### Upgrade 102 (Phase 1 - Query Optimization)

**PostgreSQL**:
```sql
-- Enable trigram extension
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create trigram indexes
CREATE INDEX CONCURRENTLY IDX_artifacts_name_trgm ON artifacts USING GIN (name gin_trgm_ops);
CREATE INDEX CONCURRENTLY IDX_artifacts_desc_trgm ON artifacts USING GIN (description gin_trgm_ops);
CREATE INDEX CONCURRENTLY IDX_versions_name_trgm ON versions USING GIN (name gin_trgm_ops);
CREATE INDEX CONCURRENTLY IDX_versions_desc_trgm ON versions USING GIN (description gin_trgm_ops);
```

#### Upgrade 103 (Phase 2 - Full-Text Search)

**PostgreSQL**:
```sql
-- Add search vector columns
ALTER TABLE artifacts ADD COLUMN search_vector tsvector;
ALTER TABLE versions ADD COLUMN search_vector tsvector;
ALTER TABLE groups ADD COLUMN search_vector tsvector;

-- Create GIN indexes
CREATE INDEX CONCURRENTLY IDX_artifacts_fts ON artifacts USING GIN(search_vector);
CREATE INDEX CONCURRENTLY IDX_versions_fts ON versions USING GIN(search_vector);
CREATE INDEX CONCURRENTLY IDX_groups_fts ON groups USING GIN(search_vector);

-- Populate search vectors for existing data
UPDATE artifacts SET search_vector =
    setweight(to_tsvector('english', COALESCE(name, '')), 'A') ||
    setweight(to_tsvector('english', COALESCE(description, '')), 'B') ||
    setweight(to_tsvector('english', COALESCE(artifactId, '')), 'A');

-- Create triggers
CREATE OR REPLACE FUNCTION update_artifact_search_vector() RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.name, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.description, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.artifactId, '')), 'A');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER artifact_search_vector_update
    BEFORE INSERT OR UPDATE ON artifacts
    FOR EACH ROW EXECUTE FUNCTION update_artifact_search_vector();
```

**MySQL**:
```sql
-- Create FULLTEXT indexes
ALTER TABLE artifacts ADD FULLTEXT INDEX FT_artifacts_search (name, description);
ALTER TABLE versions ADD FULLTEXT INDEX FT_versions_search (name, description);
```

### Backward Compatibility

1. **API Versioning**: New search parameters are optional; existing queries continue to work
2. **Graceful Degradation**: When full-text search is unavailable, fall back to LIKE queries
3. **Feature Flags**: Enable new search features via configuration

```properties
# New configuration options
apicurio.search.fulltext.enabled=true
apicurio.search.trigram.enabled=true
apicurio.search.facets.enabled=true
apicurio.search.suggestions.enabled=true
apicurio.search.cache.enabled=false
apicurio.search.cache.ttl-seconds=60
```

---

## Testing Strategy

### Unit Tests

```java
@QuarkusTest
public class SearchFilterTest {
    @Test
    void testFullTextSearchFilter() {
        SearchFilter filter = SearchFilter.ofFullText("user service api");
        assertEquals(SearchFilterType.fullText, filter.getType());
    }

    @Test
    void testDateRangeFilter() {
        Instant after = Instant.parse("2024-01-01T00:00:00Z");
        SearchFilter filter = SearchFilter.ofCreatedAfter(after);
        assertEquals(SearchFilterType.createdAfter, filter.getType());
    }
}
```

### Integration Tests

```java
@QuarkusIntegrationTest
public class FullTextSearchIT {
    @Test
    void testFullTextSearch_PostgreSQL() {
        // Create artifacts with varied content
        createArtifact("user-service", "User management service API");
        createArtifact("order-service", "Order processing microservice");

        // Search with full-text query
        var results = searchArtifacts(null, null, "user management");

        assertEquals(1, results.getCount());
        assertEquals("user-service", results.getArtifacts().get(0).getArtifactId());
    }

    @Test
    void testRelevanceRanking() {
        createArtifact("user-api", "Primary user API");
        createArtifact("user-service", "User management with user support");

        var results = searchArtifacts(null, null, "user");

        // More relevant result (more "user" mentions) should rank higher
        assertTrue(results.getArtifacts().get(0).getRelevanceScore() >=
                   results.getArtifacts().get(1).getRelevanceScore());
    }
}
```

### Performance Tests

```java
@QuarkusTest
public class SearchPerformanceTest {
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSearchPerformance_10kArtifacts() {
        // Pre-populate 10,000 artifacts
        populateTestData(10_000);

        // Search should complete within timeout
        var results = storage.searchArtifacts(
            Set.of(SearchFilter.ofDescription("test")),
            OrderBy.name, OrderDirection.asc, 0, 20
        );

        assertNotNull(results);
    }
}
```

---

## Conclusion

This design addresses the key limitations of the current SQL search implementation through a phased approach that:

1. **Optimizes existing queries** without breaking changes
2. **Adds full-text search** with database-specific implementations
3. **Introduces advanced features** like faceted search and suggestions
4. **Improves scalability** through caching and cursor pagination

The implementation maintains backward compatibility while providing a migration path for users to adopt new search capabilities as they become available.

---

## Appendix A: File References

| File | Purpose |
|------|---------|
| `AbstractSqlRegistryStorage.java` | Main search implementation |
| `SearchFilterType.java` | Filter type enumeration |
| `SearchFilter.java` | Filter model class |
| `SqlStatements.java` | SQL statement interface |
| `CommonSqlStatements.java` | Common SQL implementations |
| `PostgreSQLSqlStatements.java` | PostgreSQL-specific SQL |
| `SearchResourceImpl.java` | REST API implementation |
| `postgresql.ddl` | PostgreSQL schema |
| `mysql.ddl` | MySQL schema |
| `mssql.ddl` | SQL Server schema |
| `h2.ddl` | H2 in-memory schema |

## Appendix B: Configuration Reference

```properties
# Search feature toggles
apicurio.search.fulltext.enabled=true
apicurio.search.trigram.enabled=true
apicurio.search.facets.enabled=false
apicurio.search.suggestions.enabled=false

# Search caching
apicurio.search.cache.enabled=false
apicurio.search.cache.ttl-seconds=60
apicurio.search.cache.max-entries=1000

# Search limits
apicurio.search.max-results=1000
apicurio.search.suggestions.max=10
apicurio.search.facets.max-values=50

# Full-text search configuration
apicurio.search.fulltext.language=english
apicurio.search.fulltext.min-word-length=2
```
