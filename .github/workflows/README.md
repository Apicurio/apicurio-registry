# CI Workflows

## PR Verification Pipeline

Two top-level workflows make up the whole pipeline:

| Workflow | Contents |
|----------|----------|
| `quick-check.yaml` (**Quick Check**) | The fast PR gate: **Quick Verify** (~5 min, every PR push) and a fast UI build. No lifecycle awareness, no Decide job — it always runs the same way. Drives `lifecycle/tested` while a PR is in `lifecycle/ready-for-review`. |
| `verify.yaml` (**Verify**) | Everything else: Build (Java app + Docker images), unit tests, CLI, SDKs, console plugin, integration tests, extra tests, operator tests, and (on push to `main`) image publishing. One `decide` job, shared by every job in this workflow via `needs:`. Drives `lifecycle/full-verified`. |

Performance workflows are intentionally not PR gates:

- `perf-main.yaml` tracks Apicurio-specific capacity/regressions after merges to `main`.
- `perf-comparison.yaml` is a manually dispatched, product-neutral Confluent-compatible API
  comparison across Apicurio, Confluent, Karapace, and Redpanda. It runs sequential randomized
  repetitions and publishes raw evidence; it is not part of Verify/Decide/Verification Gate.
`verify.yaml` intentionally has a single Decide job that every other job in it
depends on, rather than being split across several independently-triggered
workflow files each with their own Decide. That used to be the design (four
separate workflow files, each recomputing "is the full suite required"
independently) so that Integration Tests and Extra Tests would show up as
their own grouped sections in the PR Checks tab — but four independent Decide
evaluations for the same commit can each see different PR label state if
anything changes between when they happen to run, which repeatedly produced
real bugs (a sibling workflow's Build silently skipped while another sibling
expected to consume it; the Verification Gate reporting success while a
sibling had not actually run). Consolidating back into one workflow with one
Decide removes that entire class of problem structurally: there is exactly
one answer to "is the full suite required for this commit" per run, so
nothing in it can disagree with anything else about it. The cost is cosmetic
— Integration Tests and Extra Tests show up as `Verify / Integration Tests
(h2-default)` etc. instead of their own workflow header — which is a better
trade than the alternative.

### Centralized Decision (`decide` job)

`verify.yaml`'s `decide` job (`verify-decide.yaml`) combines **lifecycle
scope** (PR labels) and **change detection** (path-based filtering) into one
boolean output per phase, computed once and shared by every other job in the
workflow via `needs:` and a single `if:` condition each.

**Lifecycle scope** is a live, native-fact decision — not a PR label:
- author is a maintainer or in `auto_accept` (e.g. Renovate) → full suite runs
  immediately, on every push
- otherwise → full suite runs once the PR has a current approving review
  (`gh pr view --json reviewDecision` == `APPROVED`), re-evaluated fresh on
  every `pull_request_review: submitted` event
- `orchestrator/disabled` label → full suite runs regardless (unless
  `DO NOT MERGE` is also present)
- Push to main → always full suite

Deciding this from author identity and review state instead of a bot-applied
label (the previous `lifecycle/ready-to-merge` design) removes an entire class
of races: there is no window where a run that started before a promotion could
disagree with one that started after — Decide re-evaluates live, every time.

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

The `gate` job in `verify.yaml` is the **single required check** for branch
protection. It runs with `if: always()` and aggregates every job in the same
run via `needs.*.result`. For non-push events it then does one more thing:
it fails outright if `needs.decide.outputs.lifecycle-ready != 'true'`.

That check exists because a skipped required job counts as "passing" for
branch protection purposes, and every job in `verify.yaml` is skipped by
Decide whenever the full suite was not required for this run — without it,
native "Merge pull request" would be unblocked the moment Decide+Gate
complete for any PR, regardless of whether the full suite had ever actually
run for the commit. Checking Decide's own output (the same static value that
already gated every job above via `needs:`) means this cannot disagree with
the rest of the run — there is nothing to race against, unlike a live
re-fetch of current PR/label state would be. The failure this produces
during normal PR review is expected, not a sign of anything broken: it
clears on its own once the full suite subsequently runs and passes (for a
trusted author, immediately; for everyone else, once a review lands).

The PR lifecycle orchestrator (`pr-lifecycle.js`) independently tracks
`verify.yaml`'s latest run by head SHA before applying `lifecycle/full-verified`.

This (and the single-Decide-job design above) replaces two earlier approaches:
first, configuring 24 individual jobs as required checks, which caused skipped
jobs to show as permanently pending; then, splitting the suite across four
independently-triggered workflow files, which introduced cross-workflow
Decide-disagreement races (see above); then, gating Decide on a bot-applied
`lifecycle/ready-to-merge` label with a live re-fetch in Gate to cross-check
it, which added its own race between the live re-fetch (evaluated late, after
every other job) and Decide (evaluated once, early) — a run could look "ready"
by the time Gate's live check ran even though Decide itself had skipped
everything at the start of that same run. Deciding from live author/review
facts directly (this design) needs no live re-fetch and no bot-applied label
at all, so there is nothing left to race.

## Unit Test Sharding

The unit tests are split into 7 parallel shards to reduce the critical path
(see `verify-unit-tests.yaml`, the source of truth for the exact package and
module patterns; the table below summarizes intent only). Typical durations
are control-group measurements as of 2026-08-01 from
[Discussion #8364](https://github.com/Apicurio/apicurio-registry/discussions/8364).
They are illustrative, not guaranteed: actual times vary with runner load and
cache state. The only CI-enforced budget is the 600 s per-shard warning
threshold in `verify-unit-tests.yaml`.

| Shard | Intent | Typical duration |
|-------|--------|-----------------|
| `app-rest` | REST API and Confluent-compatibility (ccompat) endpoint tests | ~4.5 min |
| `app-sql` | SQL storage variant tests, plus shared storage tests that aren't variant-specific (search backend, polling/blue-green, read-only decorator, DTOs) and SQL eventing | ~5 min |
| `app-kafkasql` | KafkaSQL storage variant | ~6 min |
| `app-gitops` | GitOps storage variant | ~4 min |
| `app-kubernetesops` | Kubernetes ConfigMaps storage variant | ~3 min |
| `app-auth` | Authentication, authorization and rate-limit tests (`auth`, `rbac`, `limits`) | ~4 min |
| `app-transport` | TLS/mTLS, HTTP security headers and CORS (`tls`, `headers`, `cors`) | ~2.5 min |
| `app-metrics` | Metrics, tracing and search-index tests (`metrics`, `search`) | ~2 min |
| `app-other` | Catch-all for everything else in `app/`: rules, ui, services, contracts, customTypes and most of `noprofile/*` (compatibility, resolver, serde, proxy, etc.), excluding the packages claimed by every other `app-*` shard | ~9 min |
| `non-app` | All modules except `app/`, `distro/docker`, `docs`, `docs/config-generator`, `docs/rest-api` (schema-util, serdes, java-sdk, cli, mcp, etc.) | ~5 min |

### How sharding works

- **app-rest**, **app-sql**, **app-kafkasql**, **app-gitops**,
  **app-kubernetesops**, **app-auth**, **app-transport**, and **app-metrics**
  each use surefire's `-Dtest=` to *include* a specific set of packages.
- **app-other** uses `-Dtest=!...` to *exclude* every package claimed by those
  include shards, catching everything else in `app/`. Each exclusion in its
  filter must pair with an inclusion in another shard, or tests go missing.
- Shard boundaries are chosen by `@TestProfile` density, not just class count.
  Surefire runs a shard in one forked JVM (`forkCount=1`, `reuseForks=true`
  by default), and each distinct profile makes Quarkus tear down and rebuild
  the application in that JVM. Concentrating many profiles in one shard is what
  exhausted its heap in issue #9265.
- **non-app** uses Maven's `-pl` to run all modules except `app` and modules that
  depend on the `app` JAR (`distro/docker`, `docs`, `docs/config-generator`, `docs/rest-api`).

### Adding new tests

Most new test classes are automatically picked up, but storage/event
subpackages are an exception. Read this before adding one:

- **In `app/`**: if the package matches `noprofile.rest` or `noprofile.ccompat`,
  it runs in `app-rest`. A `storage.impl.*` / `event.*` subpackage that is
  already covered by one of the storage-variant include filters
  (`app-sql`, `app-kafkasql`, `app-gitops`, `app-kubernetesops`) runs there.
  `auth`/`rbac`/`limits`, `tls`/`headers`/`cors`, and `metrics`/`search` run in
  `app-auth`, `app-transport`, and `app-metrics` respectively.
  Everything else lands in `app-other` (the exclusion-based shard).
- **In any other module**: runs in `non-app`.

**Warning:** `app-other`'s filter excludes *all* of `storage.**` and
`event.**`, not just the subpackages claimed by the other shards. A new
subpackage under `io.apicurio.registry.storage` or `io.apicurio.registry.event`
that isn't added to one of the include filters runs in **zero** shards, and
does so silently: `verify-unit-tests.yaml` sets
`-Dsurefire.failIfNoSpecifiedTests=false`, so no shard fails when this
happens. Any new storage or event subpackage **must** be added to an include
shard in `verify-unit-tests.yaml`, or its tests will never run in CI.

This is enforced, not left to reviewers. The **Lint and Validate** job runs:

```bash
python3 .github/scripts/verify-test-shards.py
```

which reads the shard matrix out of `verify-unit-tests.yaml`, applies
surefire's `-Dtest=` matching rules, and fails the build if any `app/` test
class is claimed by zero shards or by more than one. Run it locally before
changing any shard boundary — it is much faster than waiting for CI.

### Rebalancing shards

If one shard grows significantly slower than the others, rebalance by moving
package patterns between the `app-*` shards in `verify-unit-tests.yaml`.
Check actual CI timings with:

```bash
gh run view <run-id> --repo Apicurio/apicurio-registry --json jobs \
  --jq '.jobs[] | select(.conclusion != "skipped") |
        select(.startedAt != "0001-01-01T00:00:00Z" and .completedAt != "0001-01-01T00:00:00Z") |
        "\(.name): \((.completedAt | fromdateiso8601) - (.startedAt | fromdateiso8601))s (\(.conclusion))"'
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
| `verify.yaml` | PR, push to main | Main orchestrator: `decide` job determines what to run, `gate` (Verification Gate) is the single required check | N/A |
| `build-java`/`build-ui` (jobs in `verify.yaml`) | Called by verify | Parallel Java (`mvnw install -T 0.5C`) + UI (`npm build`) builds. Produces Docker images and build artifacts uploaded with 1-day retention. The sole build for a commit, shared by every other job in the same run via `needs:` | ~6 min |
| `verify-unit-tests.yaml` | Called by verify | Unit tests in 7 parallel shards (see above) | ~14 min (critical path) |
| `scalpel-report` (job in `verify.yaml`) | PR with java changes | Scalpel affected-module analysis in report mode; uploads a JSON artifact plus a summary for offline analysis (see [Reading the Scalpel report](#reading-the-scalpel-report)). Not in the Verification Gate. Opt out per PR with the `ci/disable-scalpel` label | ~2 min |
| `verify-integration-tests.yaml` | Called by verify | 13-job matrix across storage backends, each with Minikube | ~15 min per job |
| `verify-extras.yaml` | Called by verify | 5 parallel jobs: extra tests, UI Playwright tests, legacy V2 compatibility tests, TypeScript SDK tests, example builds | ~13 min |
| `verify-sdk.yaml` | Called by verify | Go and Python SDK verification | ~2 min |
| `verify-cli.yaml` | Called by verify | CLI native build (GraalVM) + tests on Linux and macOS. Conditional on `cli/` or `java-sdk/` changes | ~15-25 min |
| `verify-publish.yaml` | Called by verify | Push Docker images (app, UI, MCP, GitOps) to DockerHub and Quay.io. Main branch only. Uses `reusable-docker-build.yaml` for multi-arch builds | ~30-40 min |

### Reading the Scalpel report

The `scalpel-report` job runs `mode=report`. It works out which modules a PR
affects and writes `scalpel-report.json`. It does not trim anything. That job
builds the whole reactor, and so does every other job in the run. The report
describes what a trimming build would do, so nothing in it is a saving that
already happened.

The reactor splits three ways, and only the last part is a saving:

```
reactor   = buildSetSize + skippedModules
build set = affectedModules + excludedUpstreamCount
```

Since Scalpel 0.4.1 every part of that split is a native report field:
`buildSetSize`, `reactorModuleCount`, `testedModulesCount` and the
`skippedModules` list. `excludedUpstreamCount` counts upstream build
prerequisites of the affected modules: Scalpel drops them from the report and
not from the build, so they still compile. Taking `affectedModules` as the
build set therefore understates it by exactly that count.

A worked example, from a real run on this repository with the pinned version
(one Java file changed in `app`):

| field | value |
| --- | ---: |
| `affectedModules` | 5 |
| `excludedUpstreamCount` | 42 |
| `buildSetSize` | 47 |
| `testedModulesCount` | 5 |
| `skippedModules` | 10 |
| `reactorModuleCount` | 57 |

Read naively, "5 affected out of 57" looks like a 91% saving. The projection is
10 modules out of 57, which is 17.5%.

Every decision is anchored by `decisionId`, `mergeBaseId`, `headId` and
`configFingerprint`, all carried in the report, so two artifacts can be compared
without consulting git history.

The job writes `scalpel-report-summary.md` next to the JSON and into the run
summary, so the table above is already built for the run you are looking at.
The summary reads the native fields only. The summary lives in
[`scalpel-summary.sh`](../scripts/scalpel-summary.sh) and is unit tested by
[`scalpel-summary.test.sh`](../scripts/scalpel-summary.test.sh) in the
`scripts-tests.yaml` workflow. When the native counts are missing, malformed,
negative or mutually inconsistent, the summary says so instead of reading
anything as zero.

Not every run produces a decision table. When a changed file matches
`scalpel.disableTriggers` or `scalpel.fullBuildTriggers`, or when
`scalpel.excludePaths` removes every changed file, Scalpel writes a status
report instead: `status`, `reason`, and, since 0.4.1, the `triggerFile`
responsible plus `changedFiles`, `decisionId` and `timings`. The summary names
the file and reads the reason, because the reason decides which explanation
the run gets.

Two reasons would empty the reactor. They are the only two that reach Scalpel's
`trimReactorToEmpty`: `all changed files excluded by path filters` and `no
changes detected`. Only the first mentions path filters, so a summary that
keyed the empty case on that phrase alone would misreport the second. Neither
reason describes a working outcome in this repository: `.mvn/maven.config`
pins `scalpel.buildAllIfNoChanges=true`, so a trimming build runs every module
on both (the log line reads `building all modules (buildAllIfNoChanges=true)`),
and with the Scalpel default of false the zero-project reactor dies in Maven
proper with `NoGoalSpecifiedException`, because a session with no projects has
no goals. Scalpel 0.4.0 and earlier built every module here too, so the empty
build the reason's wording suggests has never been a reachable outcome on any
pin of this extension. Both facts were verified on 0.4.2 in `mode=trim` with
synthetic change sets (REG-304).

Five reasons project a full build, because Scalpel returns without touching the
reactor. Configuration stands it down in three of them, `disabled by
disableTriggers match`, `disabled by disableOnBranch` and `disabled by
disableOnBaseBranch`. In the other two it never gets far enough to compare
anything: `not a git repository` and `no base branch configured`. Those last
four come from a different jar than the rest. `extension3` writes most of what
lands in the report, but `ScalpelCore` abandons detection with a skip reason of
its own and the caller copies it in verbatim, so reading only `extension3`
yields an incomplete set. A `failed` status carrying `change detection did not
run (see build log)` leaves the reactor whole too, so it is a full build as
well, but the cause rather than the projection is the part worth chasing.

Three reasons never appear in this report as a status, because Scalpel routes
them to `target/scalpel-shadow.json`: `no modules affected by changes`, `no
modules match includePaths filters` and `disabled by -pl project selection`. A
run that hits one of those writes an ordinary report here, with no `status`
field, so it lands in the counts path rather than as a skip. The first of the
three is reachable in this repository: a change confined to a module outside
the default reactor, such as `operator/` or `mcp/`, or to a root-level file no
pom names, affects no module this job's reactor builds. Its report carries a
decision table with `buildSetSize` 0 and `skippedModules` naming all 57
modules, but a trimming build does not perform that projection on Scalpel
0.4.2, with `buildAllIfNoChanges` either way (verified with the operator-only
change set of commit `0b35b825b`): the reactor stays whole and every module
builds. The summary recognizes the zero build set and says so rather than
drawing that table.

That root-level files sit in this family is why `scalpel.excludePaths` carries
no slash-free pattern. Scalpel rewrites a pattern without a slash to match at
every depth, and 0.3.10 matched the root only, so the 0.4.x bump silently
widened the old `*.md` and `LICENSE` entries from root files to the whole
tree. Verified by probing the same in-tree change,
`app/src/test/resources/git/invalid-content-ref/README.md`, against both pins:
0.3.10 attributes it to `app` (a test fixture `GitOpsStatusTest` loads), 0.4.2
with the widened pattern excluded it and every other in-tree markdown file,
111 tracked files in all. The list this branch ships drops the slash-free
entries, which restores attribution for all of them and leaves root markdown
and LICENSE unlisted: a change confined to those projects the zero-build-set
report above instead, which under the pin builds every module.

The report also sets `fullBuildTriggered` to true on the exhaustion outcome.
Under this repository's `buildAllIfNoChanges=true` pin that is now the truth:
the trimming build runs every module. Against the Scalpel default of false the
same field states the opposite of the behaviour, because the reactor empties
and the build fails rather than building everything, which is REG-307, so the
summary still branches on `status` and the reason before it reads that field.
The summary matches each reason in full rather than by substring and refuses
to name a projection for a reason it does not know, because the same `skipped`
status covers both an empty reactor and a full build and there is no safe
default. Of the two trigger patterns, only `scalpel.disableTriggers` is set in
`.mvn/maven.config`; `scalpel.fullBuildTriggers` is left at the Scalpel default.

The summary understands report schema version 2, which is what the version
pinned in `.mvn/extensions.xml` writes today. On any other schema the summary
refuses the decision table and prints the producer version it found, rather
than reading absent fields as zero and claiming the whole reactor as a saving.
That refusal is safe but quiet, so `scalpel-summary.test.sh` also asserts that
the pinned version is one that writes schema 2. A pull request to `main` moving
the pin turns `scripts-tests.yaml` red until the script learns the newer schema.
Two gaps in that gate are worth knowing. The workflow triggers on
`pull_request` against `main`, so a bump landing any other way skips the check.
And it is not part of the Verification Gate, the single required check for
branch protection, so a red run there is a signal a reviewer has to read rather
than a merge blocker.

The job passes no `scalpel.baseBranch`, so Scalpel derives it from
`GITHUB_BASE_REF` and diffs against `origin/<base branch>`, which is
`origin/main` for most PRs but is whatever branch a PR actually targets.

#### Measured baseline

Scalpel 0.4.1 replayed over the last 40 first-parent commits of `main`, on
2026-09-16:

| outcome | runs | share |
| --- | ---: | ---: |
| empty build, every changed file matched `excludePaths` | 16 | 40.0% |
| trimmed | 11 | 27.5% |
| full build, `disableTriggers` matched | 6 | 15.0% |
| empty build, analysis found no affected module | 4 | 10.0% |
| no saving, the build set is the whole reactor | 3 | 7.5% |

Mean modules not built over all 40 runs: 53.7%. Over the 11 partially trimmed
runs alone: 13.7%.

Both empty-build rows are projections under `buildAllIfNoChanges=false`, which
`.mvn/maven.config` no longer uses: with the pin at `true` the exhaustion runs
build every module, and the zero-build-set runs build every module too, because
a trimming build never applies that decision on Scalpel 0.4.2 with the flag
either way. The rows above were counted under the pre-narrowing excludePaths
list; under the list this branch ships the split moves to 12 exhausted and 8
zero-build-set, and the total projecting a zero-module build stays 20 of 40.
The behavior statements are 0.4.2 facts; the replay rows themselves are 0.4.1
reports, whose schema is byte-identical to 0.4.2 by the hash check recorded in
REG-245. The mean under the shipped configuration is the trimmed row alone,
about 4% of module-builds, plus the test-time saving that
`scalpel.skipTestsForUpstream` would add on the trimmed runs, which this
replay did not measure and whose adoption is undecided (REG-303).

An earlier replay of the same 40 commits on 0.4.0 put the mean at 5.8%. Almost
all of that difference is one upstream fix,
[maveniverse/scalpel#184](https://github.com/maveniverse/scalpel/issues/184):
exhausting `excludePaths` used to build every module and now builds none, which
moves 16 of the 40 runs from the worst outcome to the best one. The two
remaining issues from that round are also closed.
[#185](https://github.com/maveniverse/scalpel/issues/185) was the root-aggregator
cascade, and a root-level file that no pom names now projects an empty build
rather than the whole reactor. [#187](https://github.com/maveniverse/scalpel/issues/187)
asked for the three-way split to be readable from the report, which is what the
native count fields above deliver.

Three caveats. The replay ran on 0.4.1 rather than the 0.4.2 now pinned in
`.mvn/extensions.xml`; the two ship a byte-identical report schema and differ
only in how an empty trim is applied to the session, which `mode=report` never
reaches. The replay also predates the current `scalpel.excludePaths` list, so
the 16 exhausted runs come from re-applying the current list to each commit's
changed files using Scalpel's own glob rules, and the 11 trimmed percentages are
as measured, which makes them a lower bound: excluding more files can only
shrink an affected set. And the replay harness is not in this repository, so the
table cannot be regenerated from a checkout. Treat it as a dated observation and
re-measure rather than trusting it indefinitely.

## Validation Workflows

| Workflow | Trigger | Purpose | Duration |
|----------|---------|---------|----------|
| `validate-docs.yaml` | PR (docs/**), workflow_call | Runs `docs-playbook/_build-all.sh` to validate documentation builds | ~10 min |
| `validate-openapi.yaml` | PR (openapi.json), workflow_call | Lints OpenAPI spec with `@rhoas/spectral-ruleset` | ~5 min |
| `pr-validation.yml` | `pull_request_target` opened/reopened/synchronize/edited/milestoned/demilestoned | Checks the PR body links an issue, every commit is DCO signed, and the PR plus every issue it closes carries an open milestone; flags possible duplicate PRs by linked issue or overlapping files. Independent of the lifecycle: a red check never blocks a merge, and PRs are not auto-closed. Uses `pull_request_target` (write token) instead of `pull_request` so it can comment/label on fork PRs; it never checks out the PR head, only the base branch and PR metadata via the API. The milestone activity types matter because setting a milestone is the only way to clear that check | <1 min |

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
| `pr-lifecycle.yml` | PR events, review submissions, comments, workflow_run, every 6 hours | Label-driven PR state machine. States: `ready-for-review` -> `ready-to-merge` (no triage stage — every PR starts at `ready-for-review`). Draft PRs are ignored until marked ready for review. Comment commands: `/reject`, `/merge` (toggles native GitHub auto-merge), `/unstale`, `/retry`. Reconciler runs after each event and on cron to fix inconsistent state. Stale detection (7-day warning, 14-day auto-close; 4/7 for PRs waiting on the author). PRs waiting on a maintainer are exempt from staleness entirely and instead get `lifecycle/review-overdue` at 14 days and a reviewer ping at 30, never a close. Label protection (reverts unauthorized changes). Failure notification posts a warning comment when any lifecycle job fails. | 2-5 min per event |
| `classify.yml` | Issue opened/edited, PR opened/ready for review (non-draft), workflow_dispatch | Embedding classifier (`.github/scripts/label-classification`) assigns `area/*` labels to issues and PRs, and an issue type to issues. For PRs, then picks a reviewer (`pr-reviewer-assignment.js`): assigns contributor PRs, posts a suggestion on maintainer PRs, rotates Renovate PRs. Configured by `reviewer_assignment` in `.github/pr-lifecycle.yml`; see [PR_LIFECYCLE.md](../PR_LIFECYCLE.md#reviewer-assignment). `pull_request_target`, but never checks out or runs PR code. Manual runs take `dry_run` to print scores without applying anything | 2-4 min |
| `update-openapi.yaml` | Push to main (openapi.json changes) | Auto-copies v3 OpenAPI spec to v2 path, commits if changed, then validates via `validate-openapi.yaml` | 10-15 min |
| `update-website.yaml` | Release event, workflow_dispatch | Updates `latestRelease.json` on apicurio.github.io with release metadata | 5-10 min |
| `publish-docs.yaml` | Push to main (docs/**), workflow_dispatch | Builds documentation via Antora playbook and publishes to apicurio.github.io | 15-30 min |
| `operator.yaml` | Push/PR to main (operator/**) | Operator CI: build + push temp images to ttl.sh (8h TTL), 9-group test matrix on Minikube (smoke, kafka, auth, database, feature-a, feature-b, feature-setup, OLM v0, OLM v1) plus a single-JVM "Local Tests" run of the full IT suite against the same Minikube cluster, publish to Quay.io on push only. Cancels in-progress on new push | 45-90 min |
| `image-scan.yaml` | Daily at 06:00 UTC, workflow_dispatch | Trivy vulnerability scan on `latest-snapshot` image (CRITICAL + HIGH severity). Results uploaded to GitHub Security tab as SARIF | 5-10 min |

## Reusable Workflows

| Workflow | Used by | Purpose |
|----------|---------|---------|
| `reusable-docker-build.yaml` | verify-publish, release-images | Multi-arch Docker build with QEMU + Buildx. Supports optional Maven/npm pre-build steps. Pushes to DockerHub and/or Quay.io. Includes retry logic (3 attempts, 30s wait). Configurable platforms, tags, registries | 30-60 min |
| `reusable-notify-slack.yaml` | verify, operator, release-* | Sends Slack notifications. Always posts to notification webhook; additionally posts to error webhook on failure. Payload includes workflow name, status, and link |
