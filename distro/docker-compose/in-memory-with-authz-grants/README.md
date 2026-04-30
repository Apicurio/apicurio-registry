# In-Memory Registry with Keycloak + Per-Resource Authorization

Demonstrates two-layer authorization:
- **Keycloak**: authentication + coarse-grained RBAC (who is this user, what role do they have)
- **Grants evaluator**: fine-grained per-resource authorization (can this user access this specific artifact)

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
| `user` | `user` | sr-readonly | Read `shared/*` only |

The `developer` and `user` have different RBAC roles AND different per-resource access. The key point: even if two users had the same RBAC role, grants can give them different per-resource permissions.

## How it works

```
User → Keycloak (authn + JWT with roles)
     → Registry API
       → Admin override (admins bypass everything)
       → RBAC check (sr-admin? sr-developer? sr-readonly?)
       → Owner check (artifact owners bypass grants)
       → Grants evaluator (does this user have a grant for this resource?)
       → Allow / Deny
```

1. User authenticates via Keycloak (OIDC) — gets a JWT with roles
2. Admin override: admins bypass all checks
3. RBAC: coarse-grained role check (must have sr-developer to write)
4. Owner check: artifact owners always have access to their own artifacts
5. Grants evaluator: matches the user's identity and roles against the grants file
6. For search/list: grants are translated to SQL filters so unauthorized artifacts never appear in results

## Quick start

Build the Registry image from source (from the repository root):

```bash
# Build the app and prepare the docker context
mvn install -pl distro/docker -am -DskipTests -Dcheckstyle.skip=true

# Start all services (builds the Registry image automatically)
cd distro/docker-compose/in-memory-with-authz-grants
docker compose up -d --build
```

Wait for Keycloak to start (~15 seconds), then access:
- UI: http://localhost:8888
- API: http://localhost:8081
- Keycloak: http://localhost:8090 (admin/admin)

## Demo: Testing with curl

### 1. Get tokens

```bash
get_token() {
  curl -s -X POST "http://localhost:8090/realms/registry/protocol/openid-connect/token" \
    -d "grant_type=password&client_id=apicurio-registry&username=$1&password=$2" | jq -r '.access_token'
}

ADMIN_TOKEN=$(get_token admin admin)
DEV_TOKEN=$(get_token developer developer)
USER_TOKEN=$(get_token user user)
```

### 2. Seed data as admin

```bash
# Create groups
for g in team-a team-b shared; do
  curl -s -o /dev/null -w "Create group $g: %{http_code}\n" \
    -X POST "http://localhost:8081/apis/registry/v3/groups" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"groupId\": \"$g\"}"
done

# Create artifacts
for pair in "team-a:user-events" "team-a:order-schema" "team-b:inventory-schema" "team-b:shipping-events" "shared:common-types" "shared:error-schema"; do
  g="${pair%%:*}"; a="${pair##*:}"
  curl -s -o /dev/null -w "Create $g/$a: %{http_code}\n" \
    -X POST "http://localhost:8081/apis/registry/v3/groups/$g/artifacts" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"artifactId\": \"$a\", \"artifactType\": \"JSON\", \"firstVersion\": {\"content\": {\"content\": \"{\\\"type\\\":\\\"object\\\"}\", \"contentType\": \"application/json\"}}}"
done
```

### 3. Point-access checks

```bash
# developer CAN read team-a artifact
curl -s -o /dev/null -w "developer reads team-a/user-events: %{http_code}\n" \
  -H "Authorization: Bearer $DEV_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-a/artifacts/user-events"
# → 200

# developer CANNOT read team-b artifact
curl -s -o /dev/null -w "developer reads team-b/inventory-schema: %{http_code}\n" \
  -H "Authorization: Bearer $DEV_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-b/artifacts/inventory-schema"
# → 403

# developer CAN read shared artifact
curl -s -o /dev/null -w "developer reads shared/common-types: %{http_code}\n" \
  -H "Authorization: Bearer $DEV_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/shared/artifacts/common-types"
# → 200

# readonly user CAN read shared artifact
curl -s -o /dev/null -w "user reads shared/common-types: %{http_code}\n" \
  -H "Authorization: Bearer $USER_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/shared/artifacts/common-types"
# → 200

# readonly user CANNOT read team-a artifact
curl -s -o /dev/null -w "user reads team-a/user-events: %{http_code}\n" \
  -H "Authorization: Bearer $USER_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-a/artifacts/user-events"
# → 403

# admin CAN read everything
curl -s -o /dev/null -w "admin reads team-b/inventory-schema: %{http_code}\n" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-b/artifacts/inventory-schema"
# → 200
```

### 4. Search filtering

This is the key demo — unauthorized artifacts don't appear in search results at all.

```bash
# Admin sees ALL 6 artifacts
echo "Admin search results:"
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8081/apis/registry/v3/search/artifacts?limit=100" | jq '.count, [.artifacts[] | {group: .groupId, artifact: .artifactId}]'
# → 6 artifacts across team-a, team-b, and shared

# Developer sees only team-a + shared (4 artifacts, team-b is hidden)
echo "Developer search results:"
curl -s -H "Authorization: Bearer $DEV_TOKEN" \
  "http://localhost:8081/apis/registry/v3/search/artifacts?limit=100" | jq '.count, [.artifacts[] | {group: .groupId, artifact: .artifactId}]'
# → 4 artifacts, no team-b

# Readonly user sees only shared (2 artifacts)
echo "User search results:"
curl -s -H "Authorization: Bearer $USER_TOKEN" \
  "http://localhost:8081/apis/registry/v3/search/artifacts?limit=100" | jq '.count, [.artifacts[] | {group: .groupId, artifact: .artifactId}]'
# → 2 artifacts, only shared
```

### 5. Hot-reload

Edit `grants.json` to change permissions. Changes take effect within 5 seconds — no restart needed.

```bash
# Before: developer CANNOT read team-b
curl -s -o /dev/null -w "Before: developer reads team-b/inventory-schema: %{http_code}\n" \
  -H "Authorization: Bearer $DEV_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-b/artifacts/inventory-schema"
# → 403

# Add a grant for developer to read team-b (edit grants.json)
# Add this to the grants array:
#   {"principal": "developer", "operation": "read", "resource_type": "artifact",
#    "resource_pattern_type": "prefix", "resource_pattern": "team-b/"}
#
# Also add the group grant:
#   {"principal": "developer", "operation": "read", "resource_type": "group",
#    "resource_pattern_type": "exact", "resource_pattern": "team-b"}

# Wait 5 seconds for hot-reload
sleep 6

# After: developer CAN now read team-b
curl -s -o /dev/null -w "After: developer reads team-b/inventory-schema: %{http_code}\n" \
  -H "Authorization: Bearer $DEV_TOKEN" \
  "http://localhost:8081/apis/registry/v3/groups/team-b/artifacts/inventory-schema"
# → 200
```

### 6. UI walkthrough

1. Open http://localhost:8888
2. Log in as `developer` / `developer` — you see only `team-a` and `shared` groups, no `team-b`
3. Click into `team-a` — you see `user-events` and `order-schema`
4. Search for artifacts — only team-a and shared results appear
5. Log out, log in as `user` / `user` — you see only `shared` group
6. Log out, log in as `admin` / `admin` — you see all groups and all artifacts

## Grants file

The `grants.json` file defines per-resource permissions. It's mounted into the Registry container and hot-reloaded every 5 seconds.

```json
{
  "config": {
    "admin_roles": ["sr-admin"]
  },
  "grants": [
    {"principal": "developer", "operation": "write", "resource_type": "artifact",
     "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
    {"principal": "developer", "operation": "read", "resource_type": "artifact",
     "resource_pattern_type": "prefix", "resource_pattern": "shared/"},
    ...
  ]
}
```

See `app/src/main/java/io/apicurio/registry/auth/grants/README.md` for the full grants format, authorization flow, scaling, and design documentation.

## Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Service definitions |
| `grants.json` | Per-resource permission grants (hot-reloaded every 5s) |
