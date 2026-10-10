# Per-resource authorization (grants) with Keycloak

Two layers of authorization:

- **Keycloak** authenticates users and provides coarse-grained roles (`sr-admin`, `sr-developer`,
  `sr-readonly`), enforced by Registry RBAC.
- **A grants file** gives fine-grained access to individual groups and artifacts, evaluated by the
  [Kroxylicious Authorizer API](https://github.com/kroxylicious/kroxylicious/tree/main/kroxylicious-authorizer-api)
  implementation in Registry. Grants restrict within what RBAC allows; they never widen it.

Per-resource authorization is experimental (`apicurio.features.experimental.enabled=true`).

## Services

| Service               | URL                   | Purpose                               |
|-----------------------|-----------------------|---------------------------------------|
| Keycloak              | http://localhost:8080 | Authentication and roles (admin/admin) |
| Apicurio Registry API | http://localhost:8081 | Registry with per-resource authorization |
| Apicurio Registry UI  | http://localhost:8888 | Web console                           |

## Users and access

Passwords equal the user name, except `developer2` (password `developer`).

| User        | Role         | Access granted by `grants.json`                                                         |
|-------------|--------------|-----------------------------------------------------------------------------------------|
| `admin`     | sr-admin     | Everything (`sr-admin` is listed in `config.admin_roles`)                               |
| `developer` | sr-developer | Write group `team-a` and `team-a/*` except read-denied `team-a/secret-schema`; read `team-b/public-schema`; read `shared/*` (role grant) |
| `developer2`| sr-developer | Read `shared/*` only (role grant): same role as `developer`, different access          |
| `user`      | sr-readonly  | Read `shared/*` (role grant)                                                            |

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
at startup with `Grants file not found`. To run against a
locally built registry, build the image first
(`./mvnw install -pl distro/docker -am -DskipTests`, then build `distro/docker/target/docker/Dockerfile.jvm`
as `apicurio/apicurio-registry:latest-snapshot`).

Then log in to the UI as `developer`: the group and artifact lists only show `team-a`, `shared` and
`team-b/public-schema`. Log in as `user` to see only `shared`.

## What to look at

**Point access** - every endpoint that addresses a group or artifact (REST v2/v3, Confluent
compatibility API, Iceberg catalog, MCP Registry API, content and global IDs) checks grants after
RBAC, and answers `403` when no grant matches:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/registry/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=apicurio-registry&username=developer&password=developer' | jq -r .access_token)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/apis/registry/v3/groups/team-b/artifacts/inventory-schema   # 403
```

**Search and lists** - results are filtered inside the storage query, so counts and pagination are
exact and nothing you cannot read appears:

```bash
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:8081/apis/registry/v3/search/artifacts?limit=100' \
  | jq -r '.count, (.artifacts[] | "\(.groupId)/\(.artifactId)")'
```

**Hot reload** - edit `grants.json`; the change applies within 5 seconds. For example, give
`developer` read access to all of `team-b`:

```json
{"principal": "developer", "operation": "read", "resource_type": "artifact",
 "resource_pattern_type": "prefix", "resource_pattern": "team-b/"}
```

If an edit leaves the file invalid, Registry logs an error and keeps the previous grants.

## Files

| File                 | Purpose                                         |
|----------------------|-------------------------------------------------|
| `docker-compose.yml` | Services                                        |
| `grants.json`        | Grants (the directory is mounted read-only)     |
| `demo.sh`            | Seeds data and verifies the expected access     |

The grants format, evaluation rules and all configuration options are documented in
*Configuring per-resource authorization* in the Registry documentation
(`docs/modules/ROOT/pages/getting-started/assembly-configuring-resource-authorization.adoc`).
