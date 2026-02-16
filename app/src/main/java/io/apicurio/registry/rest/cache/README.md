# HTTP Caching for Apicurio Registry

## Overview

Apicurio Registry supports HTTP caching through reverse proxy caches (such as Varnish Cache, Nginx, or CDN services).
This feature can significantly improve performance and reduce load on the Registry server by serving frequently-accessed
content directly from the cache.

**Current Status**: HTTP caching is currently available for the Registry v3 REST API. Some endpoints are not yet
cached but will be in future releases. See the Endpoint Cacheability section below for details on which endpoints
currently support caching.

### Why HTTP Caching Matters

For read-heavy workloads, HTTP caching can:

- **Reduce response times** by 50-90% for cached content
- **Decrease server load** by handling requests at the cache layer
- **Improve scalability** by reducing backend requests
- **Lower operational costs** through more efficient resource usage

Schema registries often serve the same schemas thousands of times (e.g., every time a producer/consumer starts). Caching
eliminates redundant backend requests for this immutable content.

### Browser vs. Reverse Proxy Caching

HTTP caching works at two levels:

**Browser Caching** (Client-side):

- Uses `Cache-Control` header
- Caches content in the end-user's browser
- Reduces requests from that specific user
- Not shared between users

**Reverse Proxy Caching** (Server-side):

- Uses `Surrogate-Control` header (takes precedence over `Cache-Control` for proxies)
- Caches content in a shared proxy server (Varnish, Nginx, CDN)
- Serves cached content to all users
- Significantly reduces load on Registry backend

**Registry Configuration**: Browser caching is disabled for the REST API (`Cache-Control: no-cache, no-store`) to
avoid serving stale data in browsers. Only reverse proxy caching is enabled via the `Surrogate-Control` header, which
allows shared caches to serve content to multiple users while ensuring browsers always fetch fresh content from the
cache.

## How the Cache Communicates with Registry

### Request Flow

```
Client → Cache → Registry (on cache MISS)
Client ← Cache          (on cache HIT - Registry not contacted)
```

### HTTP Headers Sent by Registry

The Registry sends several headers to control caching behavior:

#### 1. Surrogate-Control

Controls how long content can be cached:

```
Surrogate-Control: max-age=864000, immutable
```

- `max-age`: Time in seconds the content is fresh (default: 864000 = 10 days for highly cacheable content)
- `immutable`: Indicates content will never change (optional, for highly cacheable content)
- `must-revalidate`: Forces cache to revalidate when content becomes stale (for moderately cacheable content)

**Note:** Varnish ignores the last two directives, but we want to keep the header value as similar to `Cache-Control` as
possible, in case other caches respect them.

#### 2. ETag (Entity Tag)

Provides a version identifier for conditional requests:

```
ETag: "1234567890"
```

The cache can use ETags for **conditional revalidation**:

1. Client requests content with `If-None-Match: "1234567890"`
2. Registry checks if content has changed
3. If unchanged: Returns `304 Not Modified` (no body, fast response)
4. If changed: Returns `200 OK` with new content and new ETag

This allows caches to verify freshness without re-downloading content.

**Varnish-specific configuration**: Set `beresp.keep` to a multiple of `beresp.ttl` (e.g., 3×) to require full backend
refresh after the keep TTL window, otherwise only conditional requests are used to revalidate. This is an extra
safeguard in case of mistakes in ETag generation. See the Varnish example in `examples/http-caching` for configuration
details.

#### 3. Vary

Indicates which request headers affect the response:

```
Vary: Accept, Accept-Encoding, Authorization
```

The cache creates separate cache entries for different values of these headers (e.g., different content types or
encodings).

**Note:** We do not have a specific support for authorization yet, but we include the `Authorization` header in the
`Vary` header.

#### 4. X-Cache-Cacheability (Extra Header)

Informational header indicating Registry's cacheability assessment:

```
X-Cache-Cacheability: HIGH
```

Values: `HIGH`, `MODERATE`, `LOW`, `NONE`

This header helps with:

- Troubleshooting cache behavior
- Making cache policy decisions (e.g., different keep TTLs per cacheability level)
- Development and testing

Can be disabled via configuration: `apicurio.http-caching.extra-headers-enabled=false`

### Varnish-Specific Configuration

When using Varnish Cache, two important TTL parameters affect behavior:

**keep TTL**:

- Allows using ETags for conditional revalidation within this window
- After keep expires, forces full backend refresh (no conditional revalidation)
- Recommended: Set to 3-10× the normal TTL for moderately cacheable content. This can be done by extracting the
  cacheability level and expiration TTL from request headers
- Example: If `max-age=30s`, set `keep=90s` to allow conditional revalidation for 60s after staleness, then require full
  refresh

**grace TTL**:

- Improves availability during backend outages
- How long to serve stale content when the backend is unavailable or overloaded
- Allows stale-while-revalidate even if the backend is available
- Recommended: 0s (disabled) if you prefer consistency to availability. Can be set to a higher value based on your
  availability requirements or SLO targets

For detailed Varnish configuration examples, see `examples/http-caching/varnish/`.

## How Registry Determines Cacheability

Registry analyzes each endpoint and request to determine the appropriate cacheability level:

### HIGH Cacheability (Immutable Content)

Content that **almost never changes** once created:

- Content accessed by global ID, content ID, or content hash
- Specific artifact versions (not DRAFT, not version expressions)
- Default TTL: 864000s (10 days)

**When applied**:

- Version expression is a specific version number (e.g., `1.0.0`)
- Version state is not DRAFT
- Query parameter `references=PRESERVE` (content not modified)

**Cache behavior**:

- Content can be cached aggressively
- No cache invalidation needed (content is permanent)
- Includes `immutable` directive

### MODERATE Cacheability (Infrequently Changing Content)

Content that **can change** but does so infrequently:

- DRAFT artifact versions (can be updated)
- Version expressions like `branch=latest` or `branch=main` (resolved version can change)
- Content with `references=DEREFERENCE` or `references=REWRITE` (processing may change)
- Default TTL: 30s

**When applied**:

- Version state is DRAFT
- Version expression is not a specific version (e.g., `latest`, `branch=main`)
- Query parameter `references` causes content transformation
- Content has references to DRAFT versions (when dereferencing)

**Cache behavior**:

- Shorter TTL to account for potential changes
- Supports conditional revalidation (304 Not Modified)
- Use extended keep TTL for better performance during DRAFT development

### LOW Cacheability

Content with limited caching potential:

- Applied when higher quality ETags are not available
- Rare in default configuration
- Default TTL: 10s

### NONE (Not Cacheable)

Content that should not be cached:

- Write operations (POST, PUT, DELETE)
- User-specific content
- Time-sensitive operations

### Caching can be influenced by external factors

Cacheability might be influenced by configuration properties, such as:

- `apicurio.rest.mutability.artifact-version-content.enabled`
- `apicurio.http-caching.higher-quality-etags-enabled`

Consider manually purging you cache if you detect issues after major changes to your Registry data:

- Importing data into Registry
- Migration between Registry versions might perform automatic data update operations
- Database rollback or restore operations

## Endpoint Cacheability

### Highly Cacheable Endpoints

**Content by Identifier** (`HIGH` cacheability):

- `GET /apis/registry/v3/ids/globalIds/{globalId}`
- `GET /apis/registry/v3/ids/contentIds/{contentId}`
- `GET /apis/registry/v3/ids/contentHashes/{contentHash}`

These endpoints return immutable content. Once created, content never changes.

**Query parameters**:

- `references=PRESERVE`: Maintains HIGH cacheability
- `references=DEREFERENCE` or `references=REWRITE`: Reduces to MODERATE cacheability (content is transformed)
- `returnArtifactType=true`: Does not affect cacheability

**References by Identifier** (`HIGH` cacheability):

- `GET /apis/registry/v3/ids/globalIds/{globalId}/references`
- `GET /apis/registry/v3/ids/contentIds/{contentId}/references`
- `GET /apis/registry/v3/ids/contentHashes/{contentHash}/references`

Reference lists for immutable content are also immutable.

**Query parameters**:

- `refType=OUTBOUND|INBOUND`: Does not affect cacheability

**Version Content - Specific Versions** (`HIGH` cacheability):

- `GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/content`

When `{version}` is a specific version number (e.g., `1.0.0`, `2.5.3`) and the version is not in DRAFT state.

**Conditions for HIGH cacheability**:

1. Version expression is a concrete version number (not `latest`, not `branch=...`)
2. Version state is not DRAFT
3. `references=PRESERVE` (default)

**Cache headers**:

```
Surrogate-Control: max-age=864000, immutable
ETag: "<content-id>"
Vary: Accept
X-Cache-Cacheability: HIGH
```

### Moderately Cacheable Endpoints

**DRAFT Version Content** (`MODERATE` cacheability):

- `GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/content`

When the version is in DRAFT state (can be updated).

**Version Expressions** (`MODERATE` cacheability):

- `GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/latest/content`
- `GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch={branchId}/content`

Version expressions resolve to different versions over time as new versions are created.

**Content with Reference Processing** (`MODERATE` cacheability):

- Any content endpoint with `references=DEREFERENCE` or `references=REWRITE`

Processing references (especially DRAFT references) means content can change.

**Cache headers**:

```
Surrogate-Control: max-age=30, must-revalidate
ETag: "<content-id-version-state-references-hash>"
Vary: Accept
X-Cache-Cacheability: MODERATE
```

**Recommended cache configuration for MODERATE**:

- Set keep TTL to 2-3× the max-age (e.g., keep=90s for max-age=30s)
- Enables conditional revalidation during DRAFT development
- Reduces backend load while keeping content reasonably fresh

### Cache Behavior Example: DRAFT Version Updates

When working with DRAFT versions:

1. **Initial request**: Cache MISS, Registry returns DRAFT content
2. **Subsequent requests** (within 30s): Cache HIT, content served from cache
3. **DRAFT content updated**: Backend content changes, but cache still has old content
4. **Requests within 30s TTL**: Cache continues serving old content (stale but valid within TTL)
5. **After 30s TTL expires**: Cache attempts revalidation
    - If keep TTL is configured (e.g., 90s): Cache can revalidate using ETag
    - Registry detects content changed (different ETag)
    - Registry returns 200 OK with new content
    - Cache updates with fresh content
6. **New requests**: Serve updated content

This balance ensures:

- Good performance during DRAFT development (30s cache)
- Reasonable freshness (content updates within 30s)
- Efficient revalidation (using ETags, not full content download)

## Configuration

HTTP caching can be configured using the following properties:

**Note**: Default TTL values are based on typical use cases. We plan to run performance testing to gather specific
statistics and determine optimal default configuration for various workload patterns.

### Global Enable/Disable

**`apicurio.http-caching.enabled`**

- Type: Boolean (optional)
- Default: `true` if any TTL > 0, otherwise `false`
- Description: Master switch to enable/disable HTTP caching entirely

When disabled, no cache headers are sent and no cache strategies are evaluated.

### TTL Configuration

**`apicurio.http-caching.high-cacheability.max-age-seconds`**

- Type: Integer
- Default: `864000` (10 days)
- Description: Cache TTL for highly cacheable endpoints (immutable content)

Set to `0` to disable caching for highly cacheable endpoints.

**`apicurio.http-caching.moderate-cacheability.max-age-seconds`**

- Type: Integer
- Default: `30` (30 seconds)
- Description: Cache TTL for moderately cacheable endpoints (DRAFT versions, version expressions)

Shorter TTL balances performance with freshness for changing content.

**`apicurio.http-caching.low-cacheability.max-age-seconds`**

- Type: Integer
- Default: `10` (10 seconds)
- Description: Cache TTL for low cacheability endpoints

Rarely used in default configuration.

### ETag Configuration

**`apicurio.http-caching.higher-quality-etags-enabled`**

- Type: Boolean
- Default: `true`
- Description: Enable higher quality ETags that detect reference changes

When enabled:

- ETags include reference tree content IDs for MODERATE cacheability scenarios
- More accurate change detection for content with DRAFT references
- Slightly more expensive to compute

When disabled:

- ETags use random values for changing content
- Reduces cacheability to LOW for reference-dependent content
- Faster ETag computation

**Note**: Hashed ETags (SHA256 of ETag value) are automatically enabled in production profile for security.

### Extra Headers

**`apicurio.http-caching.extra-headers-enabled`**

- Type: Boolean
- Default: `true`
- Description: Send extra informational headers (X-Cache-Cacheability)

Useful for:

- Development and troubleshooting
- Making cache policy decisions
- Understanding Registry's cacheability assessment

Can be disabled in production if header overhead is a concern.

## Security Considerations

Consider configuring your cache to hide some or all server-side cache-related headers from end-users, to avoid exposing
internal implementation details. For example, you can configure Varnish to remove `Surrogate-Control`, `ETag`, and
`X-Cache-Cacheability` headers from responses before they reach the client.

## Cache Invalidation

**Current status**: Cache invalidation is not yet implemented.

For now, cached content becomes stale naturally when the TTL expires. This works well for:

- Immutable content (HIGH cacheability) - never needs invalidation
- Short TTL content (MODERATE cacheability) - invalidates within 30s

**Future work**: Active cache invalidation when content is updated or deleted. Initial implementation will likely
target Varnish Cache first (using HTTP PURGE/BAN methods). This
will allow:

- Longer TTLs for moderately cacheable content
- Immediate invalidation on content updates
- Better performance with guaranteed freshness

## Getting Started

To enable HTTP caching for your Registry deployment:

1. **Deploy a reverse proxy cache** (e.g., Varnish) in front of Registry
2. **Configure the cache** to respect `Surrogate-Control` and `ETag` headers
3. **Enable PURGE support** (optional, for future cache invalidation)
4. **Configure TTLs** via Registry configuration if defaults don't meet your needs
5. **Monitor cache effectiveness** using cache HIT/MISS headers

For a complete working example with Varnish Cache, see:

- `examples/http-caching/` - Docker Compose setup with Varnish
- `examples/http-caching/varnish/` - VCL configuration examples
- `examples/http-caching/test/` - Comprehensive test suite

For questions or issues, refer to the [Apicurio Registry documentation](https://www.apicur.io/registry/docs/) or file an
issue on GitHub.

<!--

## How to update this document

When updating this document, follow these rules:

1. Keep this section hidden to avoid distracting the target audience and do not edit it.
2. This document provides information for *users* of the Registry HTTP caching feature *not* developers. Do not include
   implementation details, unless they are relevant for users to understand the behavior of the caching feature. Do not
   include source code.
3. The document should be *clear and concise*.
    - You can use ASCII art diagrams if needed, but try to keep them simple and not too many.
4. Varnish cache is used as a recommended caching solution, but the document should not be exclusively Varnish-specific.
   Mark Varnish-specific information as such, and try to provide information that generalizes to other similar caching
   solutions. Refer users to the Varnish-specific example in `examples/http-caching`.
5. The document should include the following topics (in generally this order, but feel free to adjust as needed):
    - Short overview of caching using a reverse proxy cache:
        - Why it is important
        - Difference between browser caching and reverse proxy caching, different headers being used. Mention that
          browser caching is disabled for the REST API to avoid stale data in the browser.
        - How the cache communicates with Registry
            - What HTTP headers are sent by Registry to the cache, and what they mean
                - Include information about Etag headers, and how they can be used by the cache to determine if the
                  cached content is still valid. Mention that users can set the keep TTL (Varnish specific) to some
                  multiple of the expiration TTL to occasionally force full revalidation. Refer to the Varnish-specific
                  example for more details.
        - Short mention of relevant Varnish-specific configuration, such as keep TTL and grace TTL, and how users
        - Cacheability and how Registry determines it for different endpoints in general, details about specific
          endpoints can be provided in another section.
        - Note that cache invalidation is a future work
    - Cacheability of different endpoints, with details about the expected behavior of the caching solution for each
      endpoint. Try to avoid unnecessary details, and you can describe related endpoints together, if they have the same
      caching behavior.
    - Configuration properties and what they mean
6. This feature is work in progress, mention the following when appropriate:
    - Caching is currently only supported for v3 REST API.
    - Some endpoint do not support caching yet, but will support it in the future.
    - We plan to create and run performance testing to get some specific statistics and determine optimal default
      configuration.
    - We do not support cache invalidation yet, but plan to support it in the future, most likely only for Varnish at
      first.
    - We do not have a specific support for authorization yet, but include the `Authorization` header in the `Vary`
      header.

-->
