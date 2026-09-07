---
paths:
  - "app/src/main/java/**/rest/**/*.java"
  - "java-sdk/**/*.java"
---
# REST API Conventions

- API versioned at `/apis/registry/v3/`
- Implementation in `app/src/.../rest/v3/impl/` (e.g., `GroupsResourceImpl.java`)
- Response DTOs defined in `java-sdk` (shared with clients)
- Use `V3ApiUtil` for common response building
- Error responses: structured JSON with error code, message, detail
- Never expose internal exceptions or stack traces to clients
- Pagination: use `limit`/`offset` parameters
- When changing the API, regenerate the OpenAPI definition
- SDK modules (`java-sdk`, `go-sdk`, `python-sdk`, `typescript-sdk`) must be updated when API changes

## Error responses on non-v3 API surfaces

Registry serves several API surfaces besides v3 — Confluent-compatible (`ccompat`),
Iceberg REST Catalog, and others implemented from a foreign specification. When adding
or changing one, pick the error shape in this order:

1. **The foreign spec defines an error schema** — implement it exactly. Add a mapper
   service (see `IcebergExceptionMapperService`) and dispatch to it from
   `RegistryExceptionMapper`.
2. **The foreign spec defines no error schema** — reuse the v3 `ProblemDetails` shape,
   which is what an unrecognised path already falls back to. Do not invent a new one.

Do not vendor an error schema that nothing produces. A schema present in the OpenAPI
document but never returned generates a client bean that cannot deserialize a real
error response, and the mismatch is invisible to tests that only assert status codes.

Declare every status code the surface can actually return, not just the ones on the
happy-path error list. Exceptions that escape a resource method are mapped by
`HttpStatusCodeMap`, so a surface can emit codes its own spec never mentions —
including `401`/`403` from the auth interceptor and `503` from a storage timeout.

Cover error responses with tests that assert **both** the status code and the response
body shape.
