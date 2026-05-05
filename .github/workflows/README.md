# CI Workflows

## PR Verification Pipeline

The main PR pipeline is `verify.yaml`, which orchestrates all verification steps.
It runs on pull requests to `main` and on pushes to `main`.

### Pipeline Phases

```
decide ── lint-and-validate ── build ──┬── unit-tests (3 shards)
                                       ├── integration-tests (13 jobs)
                                       ├── extras (5 jobs)
                                       ├── sdk
                                       ├── cli-verify (conditional)
                                       ├── publish (main only) ── notify-slack
                                       └── verification-gate (aggregates all results)
```

### Centralized Decision (`decide` job)

A single `decide` job combines **lifecycle scope** (PR labels) and **change
detection** (path-based filtering) into one boolean output per test phase.
Every downstream job uses a single `if:` condition.

**Lifecycle scope** is driven by PR labels from the PR lifecycle orchestrator:
- `lifecycle/new` or no label → no tests
- `lifecycle/wip` → smoke tests only (build + unit tests)
- `lifecycle/ready-for-review` / `lifecycle/ready-to-merge` → full suite
- Push to main → always full suite

**Change detection** uses `dorny/paths-filter` to determine which areas changed:

| Flag | Paths | Phases |
|------|-------|--------|
| `java` | `app/`, `common/`, `schema-*/`, `serdes/`, `config-index/`, `java-sdk/`, `mcp/`, `distro/`, `pom.xml` | build, unit-tests, extras, sdk |
| `ui` | `ui/` | build, extras |
| `integration` | `app/`, `common/`, `integration-tests/`, `schema-*/`, `serdes/`, `distro/`, `pom.xml` | integration-tests |
| `sdk` | `java-sdk/`, `go-sdk/`, `python-sdk/`, `typescript-sdk/` | build, sdk |
| `cli` | `cli/`, `java-sdk/`, `verify-cli.yaml` | build, cli-verify |
| `ci` | `.github/workflows/**` | all test phases except cli-verify |

Docs-only or UI-only PRs skip Java unit tests and integration tests entirely.
Push to main always runs everything regardless of change detection.

### Verification Gate

The `verification-gate` job is the **single required check** for branch protection.
It runs with `if: always()` and aggregates all upstream results:

- Any job **failed** → gate fails
- PR in non-testable lifecycle state (`lifecycle/new`, etc.) → gate fails
- All jobs passed or appropriately skipped → gate passes

This replaces the previous approach of configuring 24 individual jobs as required
checks, which caused skipped jobs to show as permanently pending.

## Unit Test Sharding

The unit tests are split into 3 parallel shards to reduce the critical path
(see `verify-unit-tests.yaml`):

| Shard | What it runs | Typical duration |
|-------|-------------|-----------------|
| `app-shard1` | `noprofile.rest.**`, `noprofile.ccompat.**`, `storage.**`, `event.**` | ~16 min |
| `app-shard2` | Everything else in `app/` (auth, tls, metrics, noprofile.other, etc.) | ~12 min |
| `non-app` | All modules except `app/` (schema-util, serdes, java-sdk, cli, mcp, etc.) | ~7 min |

### How sharding works

- **app-shard1** uses surefire's `-Dtest=` to *include* specific packages.
- **app-shard2** uses `-Dtest=!...` to *exclude* those same packages, catching everything else.
- **non-app** uses Maven's `-pl` to run all modules except `app` and modules that
  depend on the `app` JAR (`distro/docker`, `docs`, `docs/config-generator`, `docs/rest-api`).

### Adding new tests

New test classes are automatically picked up:

- **In `app/`**: if the package matches `noprofile.rest`, `noprofile.ccompat`,
  `storage`, or `event`, it runs in `app-shard1`. Everything else lands in
  `app-shard2` (the exclusion-based shard).
- **In any other module**: runs in `non-app`.

No CI changes needed for new tests.

### Rebalancing shards

If one shard grows significantly slower than the other, rebalance by moving
package patterns between `app-shard1` and `app-shard2` in `verify-unit-tests.yaml`.
Check actual CI timings with:

```bash
gh run view <run-id> --repo Apicurio/apicurio-registry --json jobs | \
  python3 -c "import json,sys; [print(f'{j[\"name\"]}: {j[\"conclusion\"]}') for j in json.load(sys.stdin)['jobs']]"
```

### Why not use forkCount > 1?

Quarkus's `QuarkusTestExtension` shares application instances across test classes
with the same `@TestProfile`. With `forkCount > 1`, surefire distributes classes
across JVM forks, breaking the shared lifecycle and causing
"application already been closed" errors. CI-level sharding (separate jobs) is
the only safe way to parallelize `@QuarkusTest` classes.

## Integration Tests

`verify-integration-tests.yaml` runs a 13-job matrix across storage backends:

| Storage | Test groups |
|---------|------------|
| h2 (in-memory) | default, auth, migration, debezium, iceberg |
| postgresql | default, auth, migration |
| kafkasql | default, auth, migration, snapshotting |
| kubernetesops | kubernetesops |

Each job sets up Minikube, loads the registry Docker image, and runs the
failsafe integration tests with the appropriate storage profile
(`remote-mem`, `remote-sql`, `remote-kafka`, `remote-kubernetesops`).

The full matrix always runs when Java code changes. It is only skipped for
non-Java changes (docs, UI).

## Verification Workflows

| Workflow | Trigger | Purpose | Duration |
|----------|---------|---------|----------|
| `verify.yaml` | PR, push to main | Main orchestrator: `decide` job determines what to run, `verification-gate` is the single required check | N/A |
| `verify-build.yaml` | Called by verify | Parallel Java (`mvnw package -T 0.5C`) + UI (`npm build`) builds. Produces Docker images and build artifacts uploaded with 1-day retention | ~6 min |
| `verify-unit-tests.yaml` | Called by verify | Unit tests in 3 parallel shards (see above) | ~16 min (critical path) |
| `verify-integration-tests.yaml` | Called by verify | 13-job matrix across storage backends, each with Minikube | ~15 min per job |
| `verify-extras.yaml` | Called by verify | 5 parallel jobs: extra tests, UI Playwright tests, legacy V2 compatibility tests, TypeScript SDK tests, example builds | ~13 min |
| `verify-sdk.yaml` | Called by verify | Go and Python SDK verification | ~2 min |
| `verify-cli.yaml` | Called by verify | CLI native build (GraalVM) + tests on Linux and macOS. Conditional on `cli/` or `java-sdk/` changes | ~15-25 min |
| `verify-publish.yaml` | Called by verify | Push Docker images (app, UI, MCP, GitOps) to DockerHub and Quay.io. Main branch only. Uses `reusable-docker-build.yaml` for multi-arch builds | ~30-40 min |

## Validation Workflows

| Workflow | Trigger | Purpose | Duration |
|----------|---------|---------|----------|
| `validate-docs.yaml` | PR (docs/**), workflow_call | Runs `docs-playbook/_build-all.sh` to validate documentation builds | ~10 min |
| `validate-openapi.yaml` | PR (openapi.json), workflow_call | Lints OpenAPI spec with `@rhoas/spectral-ruleset` | ~5 min |

## Release Workflows

All release workflows trigger on `release` (type: released) events and support
`workflow_dispatch` for manual re-runs. They gate on `github.repository_owner == 'Apicurio'`
and tags starting with `3.`.

| Workflow | Purpose | Duration | Key details |
|----------|---------|----------|-------------|
| `release.yaml` | Main release orchestrator. Phase 1: version bump + commit. Phase 2: parallel builds (Java app, UI, CLI native on Linux + macOS via GraalVM). Phase 3: create GitHub release with artifacts, bump to next SNAPSHOT | 60-120 min | CLI native compilation is the bottleneck (~20-40 min per platform) |
| `release-artifacts.yaml` | Publish to Maven Central (GPG-signed), generate SBOMs (`mvn dependency:tree` + `npm list`), build Maven plugin site, publish `artifact-type-builtins` npm package. Selectable via `artifacts` input (maven, sboms, site, builtins, all) | 30-90 min | 90-min timeout on Maven Central deploy. Uses GPG key for signing |
| `release-images.yaml` | Build and push multi-arch Docker images (linux/amd64, arm64, s390x, ppc64le) for app, UI, operator, GitOps sync, and MCP server to DockerHub + Quay.io. Frees disk space first (removes Android/Haskell/.NET SDKs). Determines `latest` tag via release pagination | 90-120 min | 120-min timeout. 5 separate multi-arch builds |
| `release-sdks.yaml` | Publish SDKs: Python (Poetry + PyPI), Go (git tag + proxy verification with retry), TypeScript (npm publish). Selectable via `sdks` input | 10-30 min | Go SDK uses retry loop (5 attempts) waiting for Go module proxy |
| `release-operator.yaml` | Build + push operator image, create OLM bundle + catalog, run Minikube tests (smoke, OLM v0, OLM v1), upload dist archive to GitHub release, create PRs to community-operators (k8s + OpenShift), sync post-release changes back to main | 120-180 min | 180-min timeout. Opens PRs to 2 external repos. Has tmate debug session on failure |
| `release-milestones.yaml` | Close the current milestone and create the next patch milestone | 2-5 min | Pure GitHub API operations |
| `release-release-notes.yaml` | Generate release notes from milestone issues. Categorizes as Bug Fixes, Enhancements, or Other based on labels. Updates the GitHub release body | 5-10 min | Fetches up to 1000 closed issues |

## Automation Workflows

| Workflow | Trigger | Purpose | Duration |
|----------|---------|---------|----------|
| `pr-lifecycle.yml` | PR events, comments, reviews, workflow_run, daily schedule | Label-driven PR state machine. States: `new` -> `wip` -> `ready-for-review` -> `ready-to-merge`. Comment commands: `/accept`, `/reject`, `/ready`, `/merge`, `/auto-merge`. Auto-accepts maintainers. Stale detection (7-day warning, 14-day auto-close). Label protection (reverts unauthorized changes). 838 lines of JS logic in `.github/scripts/pr-lifecycle.js` | 2-5 min per event |
| `update-openapi.yaml` | Push to main (openapi.json changes) | Auto-copies v3 OpenAPI spec to v2 path, commits if changed, then validates via `validate-openapi.yaml` | 10-15 min |
| `update-website.yaml` | Release event, workflow_dispatch | Updates `latestRelease.json` on apicurio.github.io with release metadata | 5-10 min |
| `publish-docs.yaml` | Push to main (docs/**), workflow_dispatch | Builds documentation via Antora playbook and publishes to apicurio.github.io | 15-30 min |
| `operator.yaml` | Push/PR to main (operator/**) | Operator CI: build + push temp images to ttl.sh (8h TTL), 8-group test matrix on Minikube (smoke, kafka, auth, database, feature, feature-setup, OLM v0, OLM v1), publish to Quay.io on push. Cancels in-progress on new push | 45-90 min |
| `image-scan.yaml` | Daily at 06:00 UTC, workflow_dispatch | Trivy vulnerability scan on `latest-snapshot` image (CRITICAL + HIGH severity). Results uploaded to GitHub Security tab as SARIF | 5-10 min |

## Reusable Workflows

| Workflow | Used by | Purpose |
|----------|---------|---------|
| `reusable-docker-build.yaml` | verify-publish, release-images | Multi-arch Docker build with QEMU + Buildx. Supports optional Maven/npm pre-build steps. Pushes to DockerHub and/or Quay.io. Includes retry logic (3 attempts, 30s wait). Configurable platforms, tags, registries | 30-60 min |
| `reusable-notify-slack.yaml` | verify, operator, release-* | Sends Slack notifications. Always posts to notification webhook; additionally posts to error webhook on failure. Payload includes workflow name, status, and link |
