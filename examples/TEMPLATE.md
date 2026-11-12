# Writing an example

Every example shows one feature set end to end, and checks itself. The reference is
[`per-resource-authorization`](per-resource-authorization/). Start from the skeleton in
[`_template/`](_template/):

```bash
cp -r examples/_template examples/<feature>
```

Then replace every `<placeholder>`, rename the `*-template` container names to `*-<feature>`, and
delete the template comments. The skeleton runs as-is (`docker compose up -d && ./demo.sh`), so you
can check it works before changing anything.

## Required files

| File | Purpose |
|---|---|
| `README.md` | Summary, quick start, services, files, notes, learn more |
| `GUIDE.md` | Step-by-step walkthrough with API commands, expected output and UI screenshots |
| `docker-compose.yml` | Everything the example needs; nothing to install apart from Docker, `curl` and `jq` |
| `demo.sh` | Seeds the data, then checks the expected behaviour; exits non-zero on any failure |
| `demo-env.sh` | Optional: shell helpers to call the API by hand (`source ./demo-env.sh`) |
| `images/` | Optional: screenshots, named `NN-what-it-shows.png` in step order |
| `ci.env` | Optional: settings for CI (see [In CI](#in-ci)) |

Example-specific files (rules, schemas, configuration) live next to these, with a comment on each
non-obvious line.

## Rules

- **Two commands to run it:** `docker compose up -d` then `./demo.sh`. Success is the literal line
  `All checks passed.`
- **`demo.sh` checks behaviour, not just reachability.** Assert exact status codes and exact values,
  including the failure cases (409 on a rule violation, 403 on a denial). Each check prints one
  `ok`/`FAIL` line. It must be safe to run twice. CI runs it, so it must not need a browser or input.
- **One idea per guide step.** Show the API command, its expected output, then the same thing in the
  UI when the UI shows it. End with Clean up, Presenter notes, Troubleshooting and Using other ports.
- **Ports are overridable** through environment variables (`REGISTRY_PORT`, `UI_PORT`, ...), and the
  scripts read `API` and the other URLs from the environment.
- **Compose is commented**: every non-default setting says why it is there. Use health checks and
  `depends_on: condition: service_healthy` instead of sleeps. Reuse shared files (for example
  `distro/docker-compose/config/realm.json`) rather than copying them.
- **Select the Registry images through `REGISTRY_IMAGE`, `REGISTRY_UI_IMAGE` and
  `MCP_SERVER_IMAGE`**, defaulting to `latest-snapshot`
  (`image: ${REGISTRY_IMAGE:-apicurio/apicurio-registry:latest-snapshot}`). CI sets them to the images
  built for the pull request, and fails an example that would run any other Apicurio Registry image;
  locally they also run a local build.
- **Say what is not production-ready** (dev-mode Keycloak, relaxed issuer checks, in-memory storage).
- **Link both ways:** the README links the user documentation and the ADR; the ADR links the example.
- **Replaces older examples?** Delete them in the same PR, and update [`README.md`](README.md).

## In CI

Every directory under `examples/` with an executable `demo.sh` and a Compose file runs in CI
([`verify-examples.yaml`](../.github/workflows/verify-examples.yaml)), in its own job, on every pull
request that changes an example, the server or the UI, and on every push to `main`:

1. `docker compose up -d`, with `REGISTRY_IMAGE`, `REGISTRY_UI_IMAGE` and `MCP_SERVER_IMAGE` set to
   the images built for the pull request. The job fails first if the stack would run any other
   Apicurio Registry image. Other images (Keycloak, Kafka, ...) are pulled, with retries.
2. `./demo.sh`, then `./demo.sh` again. Both must exit 0.
3. `docker compose down -v`. On failure, the Compose logs are uploaded as an artifact.

There is nothing to register: adding the example is enough. A failure blocks the merge.

Optional settings go in `ci.env` next to `demo.sh`, one `KEY=value` per line, each with a comment
saying why:

| Key | Default | Meaning |
|---|---|---|
| `CI_TIMEOUT_MINUTES` | `20` | Job timeout |
| `CI_ENABLED` | `true` | `false` skips the example in CI; say why, and prefer fixing it |
| `CI_NEEDS_BUILD` | `false` | `true` sets up a JDK and the Maven cache, then runs the example's `build.sh` before `demo.sh` |
| `CI_MATRIX` | none | Space-separated values: one job per value, for an example too long for one job. Each job sets `CI_VARIANT` and `COMPOSE_PROFILES` to its value, so put each variant's services in a Compose profile of that name |
| `CI_SELF_MANAGED` | `false` | `true`: `demo.sh` starts and stops its own containers, for an example that must prepare something first; the runner skips its start step |
| any other key | | Exported to `docker compose` and `demo.sh`, for example `SKIP_LLM=1` |

**Java clients.** Snapshots of the client libraries (SDK, serdes, validation library) are not
published, so an example with Java code builds it from the checkout. Give it a `build.sh` that runs
`./mvnw install -Pexamples -pl examples/<name>/<module> -am -DskipTests` from the repository root,
call it from `demo.sh` when the classes are missing, and set `CI_NEEDS_BUILD=true`. See
[`rules-and-compatibility`](rules-and-compatibility/) and [`kafka-serdes`](kafka-serdes/).

To run the same check locally against a local build:

```bash
REGISTRY_IMAGE=apicurio/apicurio-registry:latest-snapshot docker compose up -d && ./demo.sh && ./demo.sh
```
