# HTTP Caching Example

This example demonstrates HTTP caching implementation for Apicurio Registry using Varnish Cache as an HTTP reverse proxy.

## Overview

This example implements **Phase 1** of the HTTP caching design, focusing on caching immutable content endpoints:

- `GET /apis/registry/v3/ids/globalIds/{globalId}`
- `GET /apis/registry/v3/ids/contentIds/{contentId}`
- `GET /apis/registry/v3/ids/contentHashes/{contentHash}`

These endpoints return content that never changes once created, making them candidates for aggressive caching with a 1-year TTL.

### Implementation Approach

**Registry (Backend):**
- CDI interceptor (`@ImmutableCache`) adds HTTP caching headers to JAX-RS Response objects
- Interceptor extracts entity IDs via reflection and builds appropriate headers
- Clean separation of concerns - caching logic is independent of business logic

**Varnish (Cache Layer):**
- VCL configuration handles cache storage, TTL, query parameter normalization
- PURGE support for cache invalidation during testing
- Debug headers (`X-Cache`, `X-Cache-Hits`) for monitoring

## Architecture

```
┌─────────┐
│ Client  │
└────┬────┘
     │ HTTP
     ▼
┌─────────────────┐
│ Varnish Cache   │  Port 8080 (or 6081 in dev mode)
│ - Cache storage │
│ - VCL logic     │
└────┬────────────┘
     │ HTTP (cache miss)
     ▼
┌─────────────────┐
│ Registry        │  Port 8081 (or 8080 in dev mode)
│ - JAX-RS API    │
│ - PostgreSQL    │
└─────────────────┘
```

## Prerequisites

- Docker and Docker Compose
- curl (for testing)
- (Optional) Apache Bench or similar for load testing

## Quick Start

### Option 1: Full Stack with Docker Compose

This runs PostgreSQL + Apicurio Registry + Varnish in containers.

```bash
# Start all services
docker compose up

# In another terminal, run tests (use full stack URLs)
VARNISH_URL=http://localhost:8080 REGISTRY_URL=http://localhost:8081 ./test-phase1.sh

# View Varnish stats
docker exec varnish varnishstat

# View Varnish logs
docker exec varnish varnishlog
```

**Access:**
- Registry via Varnish (cached): http://localhost:8080
- Registry direct (uncached): http://localhost:8081
- PostgreSQL: localhost:5432

### Option 2: Dev Mode (Varnish + Quarkus Dev)

This is faster for development iteration - run Registry in Quarkus dev mode and only Varnish in Docker.

**Terminal 1 - Start Registry in Dev Mode:**
```bash
cd ../../app
mvn quarkus:dev
```
Registry runs on port 8080.

**Terminal 2 - Start Varnish:**
```bash
cd examples/http-caching
docker compose -f docker-compose-dev.yml up
```
Varnish runs on port 6081.

**Terminal 3 - Run Tests:**
```bash
cd examples/http-caching
./test-phase1.sh
```

**Benefits:**
- Hot reload for code changes
- Faster development cycle
- Easy debugging

## Testing

### Automated Tests

Run the test script to validate caching behavior:

```bash
./test-phase1.sh
```

The script tests:
- Cache headers (Cache-Control, ETag, Vary)
- Cache hit/miss behavior
- Query parameter normalization
- Conditional requests (If-None-Match)
- Response times
- Varnish statistics

### Manual Testing

#### 1. Create a Test Artifact

```bash
curl -X POST http://localhost:8081/apis/registry/v3/groups/default/artifacts \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: test-schema" \
  -H "X-Registry-ArtifactType: AVRO" \
  -d '{"type":"record","name":"Test","fields":[{"name":"id","type":"int"}]}'
```

Note the `globalId` from the response.

#### 2. Test Cache Miss (First Request)

```bash
curl -v http://localhost:8080/apis/registry/v3/ids/globalIds/{globalId}
```

Look for:
- `X-Cache: MISS` (cache miss)
- `Cache-Control: public, immutable, max-age=31536000`
- `ETag: "{globalId}"`

#### 3. Test Cache Hit (Second Request)

```bash
curl -v http://localhost:8080/apis/registry/v3/ids/globalIds/{globalId}
```

Look for:
- `X-Cache: HIT` (cache hit!)
- `X-Cache-Hits: 1` (or higher)
- Response time should be < 10ms

#### 4. Test Conditional Request

```bash
curl -v -H "If-None-Match: \"{globalId}\"" \
  http://localhost:8080/apis/registry/v3/ids/globalIds/{globalId}
```

Should return `304 Not Modified` with no response body.

## Monitoring

### Varnish Statistics

```bash
# Real-time stats
docker exec varnish-cache varnishstat

# Specific metrics
docker exec varnish-cache varnishstat -1 -f MAIN.cache_hit -f MAIN.cache_miss

# Cache hit rate
docker exec varnish-cache varnishstat -1 | grep cache_hit

# Backend health (use the secret file)
docker exec varnish-cache varnishadm -T :6082 -S /tmp/varnish-secret backend.list
```

**Key Metrics:**
- `MAIN.cache_hit` - Number of cache hits
- `MAIN.cache_miss` - Number of cache misses
- `MAIN.backend_conn` - Backend connections (should be minimal)
- Hit rate = `cache_hit / (cache_hit + cache_miss) * 100`

### Varnish Logs

**For full stack (docker-compose.yml):**
```bash
# All requests
docker exec varnish-cache varnishlog

# Cache hits/misses only
docker exec varnish-cache varnishlog -g request -q "VCL_call eq 'HIT' or VCL_call eq 'MISS'"

# Backend requests only
docker exec varnish-cache varnishlog -g request -q "VCL_call eq 'BACKEND_FETCH'"
```

**For dev mode (docker-compose-dev.yml):**
```bash
# All requests
docker exec varnish-cache-dev varnishlog

# Cache hits/misses only
docker exec varnish-cache-dev varnishlog -g request -q "VCL_call eq 'HIT' or VCL_call eq 'MISS'"

# Backend requests only
docker exec varnish-cache-dev varnishlog -g request -q "VCL_call eq 'BACKEND_FETCH'"

# Backend health
docker exec varnish-cache-dev varnishadm -T localhost:6082 -S /tmp/varnish-secret backend.list
```

## Configuration

### Varnish Configuration

See `varnish/default.vcl` for VCL configuration.

**Key Settings:**
- **Cache TTL for immutable content:** 365 days (1 year)
- **Grace mode:** 1 hour (serve stale if backend down)
- **Cache size:** 256MB (configurable via `VARNISH_SIZE`)

### Registry Configuration

In `docker-compose.yml`, key settings:

```yaml
APICURIO_AUTH_ANONYMOUS_READ_ACCESS_ENABLED: "true"
```

This enables anonymous read access for public caching. This is required for Phase 1 implementation.

## Troubleshooting

### Varnish Not Caching

Check that Registry is returning proper headers:

```bash
curl -I http://localhost:8081/apis/registry/v3/ids/globalIds/{globalId}
```

Should see:
- `Cache-Control: public, immutable, max-age=31536000`
- `ETag: "{globalId}"`

If missing, the CDI interceptor may not be active. Verify:
1. The `@ImmutableCache` annotation is present on the methods in `IdsResourceImpl.java`
2. The application has been recompiled
3. The Registry container has been restarted

### All Requests Are Cache Misses

1. Check Varnish logs to see why:
   ```bash
   # Full stack
   docker exec varnish-cache varnishlog

   # Dev mode
   docker exec varnish-cache-dev varnishlog
   ```

2. Verify query parameters are being normalized:
   ```bash
   # These should hit the same cache entry:
   curl http://localhost:8080/.../globalIds/1?a=1&b=2
   curl http://localhost:8080/.../globalIds/1?b=2&a=1
   ```

3. Check VCL configuration in `varnish/default.vcl`

### PURGE Requests Not Working

If you get "405 Not allowed" when testing PURGE:

1. **Check VCL order**: PURGE must be handled BEFORE the GET/HEAD check in `vcl_recv`:
   ```vcl
   sub vcl_recv {
       # PURGE must come FIRST
       if (req.method == "PURGE") {
           return (purge);
       }

       # This would filter out PURGE if it comes first
       if (req.method != "GET" && req.method != "HEAD") {
           return (pass);
       }
   }
   ```

2. **Restart Varnish** after VCL changes:
   ```bash
   # Full stack
   docker compose restart varnish

   # Dev mode
   docker compose -f docker-compose-dev.yml restart
   ```

3. **Test PURGE manually**:
   ```bash
   curl -v -X PURGE http://localhost:8080/apis/registry/v3/ids/globalIds/1
   # Should return: 200 OK with "Purged: /apis/registry/v3/ids/globalIds/1"
   ```

### Backend Connection Failures

Check that Registry is healthy:

```bash
# Full stack
curl http://localhost:8081/health/live

# Dev mode (quarkus:dev)
curl http://localhost:8080/health/live
```

Check Varnish backend health:

```bash
# Full stack
docker exec varnish-cache varnishadm -T :6082 -S /tmp/varnish-secret backend.list

# Dev mode
docker exec varnish-cache-dev varnishadm -T localhost:6082 -S /tmp/varnish-secret backend.list
```

## Debugging

### Enable VCL Debug Logging

To debug VCL behavior, add logging statements:

```vcl
sub vcl_recv {
    std.log("DEBUG: method=" + req.method + " url=" + req.url + " client=" + client.ip);
    # ... rest of your code
}
```

Then watch the logs:
```bash
docker exec varnish-cache varnishlog
```

### Filter Varnish Logs

Show only cache hits/misses:
```bash
docker exec varnish-cache varnishlog -g request -q "VCL_call eq 'HIT' or VCL_call eq 'MISS'"
```

Show only backend requests:
```bash
docker exec varnish-cache varnishlog -g request -q "VCL_call eq 'BACKEND_FETCH'"
```

Show requests for a specific URL:
```bash
docker exec varnish-cache varnishlog -q "ReqURL ~ '/ids/globalIds/'"
```

### Test Cache Headers Manually

```bash
# First request (should be MISS)
curl -v http://localhost:8080/apis/registry/v3/ids/globalIds/1 2>&1 | grep -E "X-Cache|Cache-Control|ETag"

# Second request (should be HIT)
curl -v http://localhost:8080/apis/registry/v3/ids/globalIds/1 2>&1 | grep -E "X-Cache|Cache-Control|ETag"

# Conditional request with ETag
curl -v -H "If-None-Match: \"1\"" http://localhost:8080/apis/registry/v3/ids/globalIds/1
# Should return: 304 Not Modified
```

### Inspect Cache Contents

Check what's cached:
```bash
docker exec varnish-cache varnishadm -T :6082 -S /tmp/varnish-secret ban.list
```

View cache statistics:
```bash
docker exec varnish-cache varnishstat -1
```

### Clear All Cache

```bash
# Ban all cached objects
docker exec varnish-cache varnishadm -T :6082 -S /tmp/varnish-secret "ban req.url ~ ."
```

## References

- [HTTP Caching Design](../../.design/http-caching/DESIGN.md)
- [HTTP Caching Notes](../../.design/http-caching/NOTES.md)
- [Phase 1 Implementation Plan](../../.design/http-caching/PHASE1.md)
- [Varnish Documentation](https://varnish-cache.org/docs/)
- [RFC 7234 - HTTP Caching](https://datatracker.ietf.org/doc/html/rfc7234)
