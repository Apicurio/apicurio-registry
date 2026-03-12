# HTTP Caching Example with Varnish Cache

This example demonstrates how to deploy Apicurio Registry with Varnish Cache 7.4 as an HTTP reverse proxy to improve
performance and reduce backend load through HTTP caching.

**Current Status**: HTTP caching is available for the Registry v3 REST API. Some endpoints support caching now, with
additional endpoints planned for future releases. See the main
[HTTP Caching README](../../app/src/main/java/io/apicurio/registry/rest/cache/README.md) for details on caching
behavior and configuration.

## Prerequisites

- Docker and Docker Compose
- For dev mode: Maven 3.8+ and Java 17+

## Quick Start

### Full Stack Deployment

This runs PostgreSQL, Registry, Varnish, and UI in containers:

```bash
# Start all services
docker compose up

# Access Registry through Varnish (cached)
curl http://localhost:8081/apis/registry/v3/system/info

# Run automated tests
cd test
./setup-and-run-tests.sh
```

**Service Endpoints:**

- **Registry via Varnish (cached)**: http://localhost:8081 ← **Use this for best performance**
- Registry direct (no cache): http://localhost:8080
- Registry UI: http://localhost:8888 (configured to use cached endpoint)
- Varnish admin: localhost:6080

### Development Mode

Run Registry in Quarkus dev mode with hot reload:

**Terminal 1 - Start Registry:**

```bash
cd ../../app
mvn quarkus:dev -Dapicurio.rest.mutability.artifact-version-content.enabled=true
```

**Terminal 2 - Start Varnish and UI:**

```bash
cd examples/http-caching
docker compose -f docker-compose-dev.yaml up
```

**Terminal 3 - Run Tests:**

```bash
cd examples/http-caching/test
./setup-and-run-tests.sh
```

Registry runs on port 8080 (direct), Varnish on port 8081 (cached), UI on port 8888.

## Running Tests

### Automated Test Suite

The test suite validates caching behavior for all supported endpoints:

```bash
cd test

# Run all tests with default images
./setup-and-run-tests.sh

# Run with custom Registry image
./setup-and-run-tests.sh --registry-image quay.io/myorg/apicurio-registry:my-build

# Show help
./setup-and-run-tests.sh --help
```

The test suite covers:

- Cache headers (Surrogate-Control, ETag, Vary, X-Cache-Cacheability)
- Cache hit/miss behavior
- Conditional requests (If-None-Match → 304 Not Modified)
- DRAFT version caching (30s TTL)
- Immutable content caching (10 days TTL)
- Reference handling (PRESERVE, DEREFERENCE, REWRITE)
- Version expressions (latest, branch=...)

## Monitoring

### Varnish Statistics

View cache effectiveness:

```bash
# Real-time statistics
docker exec varnish-cache varnishstat

# Cache hit/miss counts
docker exec varnish-cache varnishstat -1 -f MAIN.cache_hit -f MAIN.cache_miss

# Backend connections (should be low)
docker exec varnish-cache varnishstat -1 -f MAIN.backend_conn
```

**Key Metrics:**

- `MAIN.cache_hit` / `MAIN.cache_miss` - Hit rate indicates cache effectiveness
- `MAIN.backend_conn` - Number of backend connections (lower is better)
- Target hit rate: >80% for read-heavy workloads

### Varnish Logs

Monitor cache behavior:

```bash
# All requests
docker exec varnish-cache varnishlog

# Cache hits/misses only
docker exec varnish-cache varnishlog -g request -q "VCL_call eq 'HIT' or VCL_call eq 'MISS'"

# Backend requests only
docker exec varnish-cache varnishlog -g request -q "VCL_call eq 'BACKEND_FETCH'"
```

### Backend Health

Check backend connectivity:

```bash
docker exec varnish-cache varnishadm -T :6080 -S /tmp/varnish-secret backend.list
```

## Configuration

### Varnish Configuration (VCL)

See `varnish/default.vcl` for the complete configuration. Key features:

- **Query parameter normalization**: `?b=2&a=1` and `?a=1&b=2` hit the same cache entry
- **Conditional revalidation**: Uses ETags for efficient cache validation
- **PURGE/BAN support**: For future cache invalidation (not yet implemented in Registry)
- **Keep TTL**: Set to 3× the expiration TTL to extend conditional revalidation window
- **Grace period**: Serves stale content when backend is unavailable

**Configuration parameters** (in VCL):

```vcl
# Extract cacheability and TTL from Registry headers
set beresp.keep = beresp.ttl * 3;  # Conditional revalidation window
set beresp.grace = 10s;             # Serve stale if backend down
```

### Registry Configuration

Key settings in `docker-compose.yaml`:

```yaml
# Enable anonymous read access (required for caching)
APICURIO_AUTH_ANONYMOUS_READ_ACCESS_ENABLED: "true"

# Enable DRAFT version mutability (for testing)
apicurio.rest.mutability.artifact-version-content.enabled: "true"
```

For Registry HTTP caching configuration options, see the main
[HTTP Caching README](../../app/src/main/java/io/apicurio/registry/rest/cache/README.md#configuration).

## Production Deployment Considerations

When deploying this setup to production:

### 1. Security

**PURGE ACL** (`varnish/default.vcl`):

The example allows PURGE from any IP for testing. In production, restrict to trusted IPs:

```vcl
acl purge_acl {
    "127.0.0.1";           # Localhost only
    "10.0.0.0"/8;          # Internal network (adjust to your network)
}

sub vcl_recv {
    if (req.method == "PURGE") {
        if (!client.ip ~ purge_acl) {
            return (synth(405, "Not allowed"));
        }
        return (purge);
    }
}
```

**Hide debug headers** (`varnish/default.vcl`):

Remove internal cache headers before sending responses to clients:

```vcl
sub vcl_deliver {
    # Remove debug headers in production
    unset resp.http.X-Debug-Cache;
    unset resp.http.X-Debug-Cache-Hits;
    unset resp.http.X-Debug-Cache-Age;
    # Keep X-Cache-Cacheability if useful for monitoring
}
```

### 2. Cache Sizing

Adjust `VARNISH_SIZE` based on your workload:

- Default: 256MB (suitable for testing)
- Production: Start with 1-2GB, monitor `MAIN.n_lru_nuked` (evictions due to space)
- Scale up if eviction rate is high

### 3. Backend Health Checks

Review `.probe` configuration in `varnish/default.vcl` and adjust intervals/timeouts for your environment.

### 4. TLS Termination

Varnish does not handle TLS. Place an HTTPS terminator (nginx, HAProxy, cloud load balancer) in front of Varnish:

```
Client → HTTPS Terminator → Varnish → Registry
```

### 5. Cache Invalidation

Cache invalidation is not yet implemented in Registry. Content becomes stale naturally after TTL expires. Future
releases will support active invalidation via HTTP PURGE/BAN, initially for Varnish.

## Troubleshooting

### All Requests Are Cache Misses

1. Check that Registry returns proper headers:
   ```bash
   curl -I http://localhost:8080/apis/registry/v3/ids/globalIds/1
   ```
   Should see: `Surrogate-Control: max-age=...` and `ETag: "..."`

2. Check Varnish logs for cache decisions:
   ```bash
   docker exec varnish-cache varnishlog
   ```

3. Verify VCL is loaded correctly:
   ```bash
   docker exec varnish-cache varnishadm -T :6080 -S /tmp/varnish-secret vcl.list
   ```

### Backend Connection Failures

1. Check Registry health:
   ```bash
   curl http://localhost:8080/health/live
   ```

2. Check Varnish backend status:
   ```bash
   docker exec varnish-cache varnishadm -T :6080 -S /tmp/varnish-secret backend.list
   ```

3. Review Varnish logs for backend errors:
   ```bash
   docker exec varnish-cache varnishlog -g request -q "VCL_call eq 'BACKEND_ERROR'"
   ```

### VCL Configuration Errors

After changing `varnish/default.vcl`, restart Varnish:

```bash
docker compose restart varnish
```

Check logs for VCL compilation errors:

```bash
docker logs varnish-cache
```

### Clear All Cached Content

```bash
# Ban all cached objects
docker exec varnish-cache varnishadm -T :6080 -S /tmp/varnish-secret "ban req.url ~ ."

# Verify ban is active
docker exec varnish-cache varnishadm -T :6080 -S /tmp/varnish-secret ban.list
```

## Additional Resources

- [HTTP Caching Configuration Reference](../../app/src/main/java/io/apicurio/registry/rest/cache/README.md) - Registry
  caching behavior and configuration
- [Test Suite Documentation](test/README.md) - Automated test details
- [Varnish Documentation](https://varnish-cache.org/docs/) - Official Varnish docs
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/) - Full Registry documentation

<!--

## How to update this document

When updating this document, follow these rules:

1. Keep this section hidden to avoid distracting the target audience and do not edit it.
2. This document provides information for *administrators* that want to evaluate and deploy the Registry HTTP caching
   feature, *not* developers. Do not include implementation details, unless they are relevant for administrators to
   understand the configuration and behavior of the caching feature. Minimize source code excerpts, prefer referring
   users to the files.
3. Do not explain general HTTP caching concepts and how Registry uses HTTP caching, as that is covered in
   `app/src/main/java/io/apicurio/registry/rest/cache/README.md`. Focus on providing information about this specific
   example, and how to use it to evaluate the caching feature.
4. Review the example files, such as `docker-compose.yaml`, `varnish/default.vcl`, and include relevant information from
   them in this document, for example:
    - Changes needed in production environment. Assume administrators will use this example as a reference when
      configuring the caching feature in their production environment.
5. The document should be *clear and concise*.
    - You can use ASCII art diagrams if needed, but try to keep them simple and not too many.
6. This example is Varnish-specific. Assume the administrators are going to use Varnish as their caching solution, and
   include Varnish-specific information and configuration details.
7. The document should include the following topics (in generally this order, but feel free to adjust as needed):
    - Short overview of the example
    - Quick start guide with instructions on how to run the example
    - Guide on how to run the tests
    - *Short* troubleshooting section.
8. This feature is work in progress, see details about it in ``
   app/src/main/java/io/apicurio/registry/rest/cache/README.md` instruction section.
9. When providing commands, test them to make sure they work as expected.
10. If there are ways to simplify this document that require changes to the example files, such as `docker-compose.yaml`
    or `varnish/default.vcl`, feel free to make those changes and update the document accordingly. For example, the
    names
    of the services in docker both docker compose files can be the same and be simplified.

-->
