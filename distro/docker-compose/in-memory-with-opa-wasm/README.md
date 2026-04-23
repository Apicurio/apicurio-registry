# In-Memory Registry with Keycloak + OPA WASM Per-Resource Authorization

Demonstrates two-layer authorization:
- **Keycloak**: authentication + coarse-grained RBAC (who is this user, what role do they have)
- **OPA WASM**: fine-grained per-resource authorization (can this user access this specific artifact)

## Services

| Service | Port | Purpose |
|---------|------|---------|
| Keycloak | 8090 | Authentication + RBAC roles |
| Apicurio Registry API | 8081 | Schema/API registry with per-resource auth |
| Apicurio Registry UI | 8888 | Web console |

## Users (from Keycloak)

| User | Password | RBAC Role | Per-Resource Access |
|------|----------|-----------|---------------------|
| `admin` | `admin` | sr-admin | Everything (admin role bypasses resource checks) |
| `developer` | `developer` | sr-developer | Read+Write `team-a/*` artifacts, Read `shared/*` |
| `developer2` | `developer` | sr-developer | Read+Write `team-b/*` artifacts, Read `shared/*` |
| `user` | `user` | sr-readonly | Read `shared/*` only |

Both `developer` and `developer2` have the same RBAC role (`sr-developer`) but different per-resource access. This is the key difference from plain RBAC — same role, different permissions based on the resource.

## How it works

1. User authenticates via Keycloak (OIDC) — gets a JWT with roles
2. Registry checks RBAC first (Keycloak roles: sr-admin, sr-developer, sr-readonly)
3. If RBAC passes, Registry checks per-resource authorization via OPA WASM
4. OPA evaluates the compiled Rego policy against the grants data
5. Allow or deny

```
User → Keycloak (authn + JWT with roles)
     → Registry API
       → RBAC check (sr-admin? sr-developer? sr-readonly?)
       → OPA WASM check (does this user have a grant for this resource?)
       → Allow / Deny
```

## Quick start

```bash
docker compose up -d
```

Wait for Keycloak to start (~15 seconds), then access:
- UI: http://localhost:8888
- API: http://localhost:8081
- Keycloak: http://localhost:8090 (admin/admin)

## Testing with curl

Get a token for each user:

```bash
# Admin token
ADMIN_TOKEN=$(curl -s -X POST "http://localhost:8090/realms/registry/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=registry-api&username=admin&password=admin" | jq -r '.access_token')

# Developer token (team-a access)
DEV_TOKEN=$(curl -s -X POST "http://localhost:8090/realms/registry/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=registry-api&username=developer&password=developer" | jq -r '.access_token')

# Developer2 token (team-b access)
DEV2_TOKEN=$(curl -s -X POST "http://localhost:8090/realms/registry/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=registry-api&username=developer2&password=developer" | jq -r '.access_token')

# Read-only user token
USER_TOKEN=$(curl -s -X POST "http://localhost:8090/realms/registry/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=registry-api&username=user&password=user" | jq -r '.access_token')
```

Create artifacts as admin:

```bash
# Create groups
curl -X POST "http://localhost:8081/apis/registry/v3/groups" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"groupId": "team-a"}'

curl -X POST "http://localhost:8081/apis/registry/v3/groups" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"groupId": "team-b"}'

curl -X POST "http://localhost:8081/apis/registry/v3/groups" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"groupId": "shared"}'

# Create artifacts in each group
curl -X POST "http://localhost:8081/apis/registry/v3/groups/team-a/artifacts" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"artifactId": "schema-1", "artifactType": "JSON", "firstVersion": {"content": {"content": "{\"type\":\"object\"}", "contentType": "application/json"}}}'

curl -X POST "http://localhost:8081/apis/registry/v3/groups/team-b/artifacts" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"artifactId": "schema-2", "artifactType": "JSON", "firstVersion": {"content": {"content": "{\"type\":\"string\"}", "contentType": "application/json"}}}'

curl -X POST "http://localhost:8081/apis/registry/v3/groups/shared/artifacts" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"artifactId": "common-schema", "artifactType": "JSON", "firstVersion": {"content": {"content": "{\"type\":\"number\"}", "contentType": "application/json"}}}'
```

Test per-resource access:

```bash
# developer CAN read team-a artifact
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $DEV_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-a/artifacts/schema-1"
# → 200

# developer CANNOT read team-b artifact
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $DEV_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-b/artifacts/schema-2"
# → 403

# developer2 CAN read team-b artifact
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $DEV2_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-b/artifacts/schema-2"
# → 200

# developer2 CANNOT write to team-a
curl -s -o /dev/null -w "%{http_code}" -X PUT \
  -H "Authorization: Bearer $DEV2_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "hacked"}' \
  "http://localhost:8081/apis/registry/v3/groups/team-a/artifacts/schema-1"
# → 403

# read-only user CAN read shared artifact
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $USER_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/shared/artifacts/common-schema"
# → 200

# read-only user CANNOT read team-a artifact
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $USER_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-a/artifacts/schema-1"
# → 403

# admin CAN do everything
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-b/artifacts/schema-2"
# → 200
```

## Grants file

Edit `grants.json` to change per-resource permissions. The file is mounted read-only into the Registry container. To apply changes, restart the Registry container:

```bash
# Edit grants.json, then:
docker compose restart apicurio-registry
```

## Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Service definitions |
| `grants.json` | Per-resource permission grants (who can access what) |
| `registry-authz.rego` | Rego policy source (generic authorization logic) |
| `registry-authz.wasm` | Compiled WASM policy (loaded by Registry at startup) |
