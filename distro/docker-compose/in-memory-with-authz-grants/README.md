# Per-resource authorization (access rules) with Keycloak

Two layers of authorization:

- **Keycloak** authenticates users and provides coarse-grained roles (`sr-admin`, `sr-developer`,
  `sr-readonly`), enforced by Registry RBAC.
- **An access rules file** (`access.rules`) gives fine-grained access to individual groups and
  artifacts. It uses the [Kroxylicious ACL rules format](https://github.com/kroxylicious/design/blob/main/proposals/009-authorizer.md),
  shared with Kroxylicious. The rules restrict within what RBAC allows; they never widen it.

## Services

| Service               | URL                   | Purpose                               |
|-----------------------|-----------------------|---------------------------------------|
| Keycloak              | http://localhost:8080 | Authentication and roles (admin/admin) |
| Apicurio Registry API | http://localhost:8081 | Registry with per-resource authorization |
| Apicurio Registry UI  | http://localhost:8888 | Web console                           |

## Users and access

Passwords equal the user name, except `developer2` (password `developer`).

| User        | Role         | Access allowed by `access.rules`                                                        |
|-------------|--------------|-----------------------------------------------------------------------------------------|
| `admin`     | sr-admin     | Everything (RBAC admins bypass the rules)                                               |
| `developer` | sr-developer | Write group `team-a` and `team-a/*` except denied `team-a/secret-schema`; read `team-b/public-schema`; read `shared/*` (role rule) |
| `developer2`| sr-developer | Read `shared/*` only (role rule): same role as `developer`, different access           |
| `user`      | sr-readonly  | Read `shared/*` (role rule)                                                             |
| _anonymous_ | none         | Read `shared/common-types` only (`$anonymous` role rule)                                |

## Run it

```bash
docker compose up -d
./demo.sh          # seeds data, then checks point access and search filtering (needs curl, jq)
```

`demo.sh` prints one line per check and exits non-zero if any check fails.

If the default ports are in use, override them for both commands:

```bash
export KEYCLOAK_PORT=18080 REGISTRY_PORT=18081 UI_PORT=18888
docker compose up -d
KEYCLOAK=http://localhost:$KEYCLOAK_PORT API=http://localhost:$REGISTRY_PORT/apis ./demo.sh
```

With Podman on macOS, run Compose from a path under your home directory or `/private/tmp`
rather than `/tmp`: the `/tmp` symlink cannot be shared into the VM, and the registry then fails
at startup with `Access rules file not found`. To run against a
locally built registry, build the image first
(`./mvnw install -pl distro/docker -am -DskipTests`, then build `distro/docker/target/docker/Dockerfile.jvm`
as `apicurio/apicurio-registry:latest-snapshot`).

Then log in to the UI as `developer`: the group and artifact lists only show `team-a`, `shared` and
`team-b/public-schema`. Log in as `user` to see only `shared`.

## What to look at

**Point access** - every endpoint that addresses a group or artifact (REST v2/v3, Confluent
compatibility API, Iceberg catalog, MCP Registry API, content and global IDs) checks the rules
after RBAC, and answers `403` when no rule allows the request:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/registry/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=apicurio-registry&username=developer&password=developer' | jq -r .access_token)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/apis/registry/v3/groups/team-b/artifacts/inventory-schema   # 403
```

**Creation is checked on the name too** - `developer` can write to group `team-a`, but cannot
create an artifact named `team-a/secret-schema`, because that name is denied: otherwise creating it
would make them its owner and bypass the deny.

**Anonymous access is opt-in per resource** - anonymous read access is enabled, but unauthenticated
callers can read only what the rules allow to the `$anonymous` role:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/apis/registry/v3/groups/shared/artifacts/common-types  # 200
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/apis/registry/v3/groups/shared/artifacts/error-schema  # 401
```

**Search and lists** - results are filtered inside the storage query, so counts and pagination are
exact and nothing you cannot read appears:

```bash
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8081/apis/registry/v3/search/artifacts?limit=100' \
  | jq -r '.count, (.artifacts[] | "\(.groupId)/\(.artifactId)")'
```

**Hot reload** - edit `access.rules`; the change applies within 5 seconds. For example, give
`developer` read access to all of `team-b` by adding, before `otherwise deny;`:

```
allow User with name = "developer" to READ Artifact with name like "team-b/*";
```

If an edit leaves the file invalid, Registry logs an error and keeps the previous rules.

## Files

| File                 | Purpose                                         |
|----------------------|-------------------------------------------------|
| `docker-compose.yml` | Services                                        |
| `access.rules`       | Access rules (the directory is mounted read-only) |
| `demo.sh`            | Seeds data and verifies the expected access     |

The rules format, evaluation and all configuration options are documented in
*Configuring per-resource authorization* in the Registry documentation
(`docs/modules/ROOT/pages/getting-started/assembly-configuring-resource-authorization.adoc`).
