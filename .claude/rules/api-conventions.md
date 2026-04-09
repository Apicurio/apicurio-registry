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
