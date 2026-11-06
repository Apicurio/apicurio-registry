# Per-resource authorization (access rules) with Keycloak

A complete, local example of Apicurio Registry's per-resource authorization:

- **Keycloak** authenticates users and gives them a Registry role (`sr-admin`, `sr-developer`,
  `sr-readonly`), enforced by Registry role-based access control (RBAC).
- **An access rules file** ([`access.rules`](access.rules)) gives fine-grained access to individual
  groups and artifacts. It uses the
  [Kroxylicious ACL rules format](https://github.com/kroxylicious/design/blob/main/proposals/009-authorizer.md).
  RBAC is enabled here, so the rules restrict within what each role allows; they never widen it.

**New to this? Follow the [step-by-step guide](GUIDE.md).** It walks through every feature in the
API and in the UI, with commands, expected output and screenshots, in about 30 minutes, and works
as a script for a team demo.

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
docker compose up -d     # starts Keycloak, then the Registry and its UI
./demo.sh                # creates sample data, then checks the expected access
```

`demo.sh` prints one line per check and ends with `All checks passed.` (it exits non-zero if a check
fails). Then open the UI at <http://localhost:8888> and log in as `developer` / `developer`.

To explore the API yourself, load the helper commands:

```bash
source ./demo-env.sh
status developer GET /groups/team-b/artifacts/inventory-schema   # 403
visible developer                                                 # what developer's search returns
```

Stop and remove everything with `docker compose down -v`.

## Services

| Service               | URL                   | Purpose                                  |
|-----------------------|-----------------------|------------------------------------------|
| Keycloak              | http://localhost:8080 | Authentication and roles (admin console: `admin` / `admin`) |
| Apicurio Registry API | http://localhost:8081 | Registry with per-resource authorization |
| Apicurio Registry UI  | http://localhost:8888 | Web console                              |

## Users and access

Passwords equal the user name, except `developer2` (password `developer`).

| User        | Role         | Access allowed by `access.rules` |
|-------------|--------------|----------------------------------|
| `admin`     | sr-admin     | Everything: RBAC administrators bypass the rules |
| `developer` | sr-developer | Write group `team-a` and `team-a/*`, except the denied `team-a/secret-schema`; read group `team-b` and `team-b/public-schema`; read group `shared` and `shared/*` (role rule) |
| `developer2`| sr-developer | Read group `shared` and `shared/*` only (role rule): same role as `developer`, different access |
| `user`      | sr-readonly  | Read group `shared` and `shared/*` (role rule) |
| _anonymous_ | none         | Read `shared/common-types` only (`$anonymous` role rule) |

`demo.sh` creates the sample data as `admin`, so `admin` owns it. That matters: by default, the
owner of a resource bypasses the access rules for it (see step 11 of the guide).

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough (API and UI) and presenter notes |
| [`images/`](images) | The guide's UI screenshots |
| [`docker-compose.yml`](docker-compose.yml) | The three services; the Registry configuration is in its `environment` |
| [`access.rules`](access.rules) | The access rules; this directory is mounted read-only, and edits apply within seconds |
| [`demo.sh`](demo.sh) | Creates the sample data and checks the expected access |
| [`demo-env.sh`](demo-env.sh) | Shell helpers (`status`, `call`, `visible`, `denials`) to call the API as any user |
| [`../../distro/docker-compose/config/realm.json`](../../distro/docker-compose/config/realm.json) | The Keycloak realm: users, roles and clients |

## Notes

- **Other ports:** if 8080, 8081 or 8888 are in use, see
  [Using other ports](GUIDE.md#using-other-ports) in the guide.
- **Podman on macOS:** run Compose from a path under your home directory, not under `/tmp`. The
  `/tmp` symlink cannot be shared into the Podman VM, and the registry then fails at startup with
  `Access rules file not found`.
- **Run from this directory:** `docker-compose.yml` mounts the shared Keycloak realm from
  `distro/docker-compose/config/`, so keep the example inside the repository checkout.
- **Local build:** the example uses the `latest-snapshot` images. To run a locally built registry,
  build it (`./mvnw install -pl distro/docker -am -DskipTests`), then build
  `distro/docker/target/docker/Dockerfile.jvm` as `apicurio/apicurio-registry:latest-snapshot`.
- **Local only:** Keycloak runs in development mode over HTTP, and the token issuer check is relaxed
  so the browser and the registry container can share one Keycloak. Do not use this configuration
  in production.

## Learn more

- *Configuring per-resource authorization* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-configuring-resource-authorization.adoc)):
  the rules format, configuration, and limitations.
- [ADR-0005](../../adr/0005-per-resource-authorization.md): how it is implemented.
