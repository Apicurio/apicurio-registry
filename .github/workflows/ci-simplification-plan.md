# CI Simplification Plan for Apicurio Registry

## Executive Summary

This document proposes a comprehensive simplification of the Apicurio Registry CI/CD pipeline. The current setup has **27 workflow files** with significant redundancy, duplicated code, and inefficient job structures. This plan focuses on reducing complexity, improving maintainability, and optimizing CI execution time.

---

## Current State Analysis

### Workflow Inventory (27 files)

| Category | Workflows | Trigger |
|----------|-----------|---------|
| **Core Verification** | `verify.yaml`, `integration-tests.yaml`, `build-only.yaml` | PR/Push |
| **Operator** | `operator.yaml` | PR/Push (operator/** paths) |
| **Releases** | `release.yaml`, `release-images.yaml`, `release-maven-artifacts.yaml`, `release-operator.yaml`, `release-sdk-*.yaml` (4 files), `release-artifact-type-builtins.yaml`, `release-release-notes.yaml`, `release-sboms.yaml`, `release-milestones.yaml`, `release-maven-site.yaml` | Manual/Tag |
| **Automation** | `dependabot-autoapprove.yaml`, `dependabot-automerge.yaml` | Dependabot PRs |
| **Validation** | `validate-docs.yaml`, `validate-openapi.yaml` | Push/PR |
| **Publishing** | `publish-docs.yaml`, `update-website.yaml`, `update-openapi.yaml` | Push to main |
| **Scanning** | `image-scan.yaml`, `qodana.yaml` | Scheduled |
| **Snapshot** | `maven-snapshot-release.yaml`, `tool-exportV1-release.yaml` | Push to main |

### Issues Identified

#### 1. **Duplicated Build Steps Across Workflows**

The same build command appears in **5+ workflows** with minor variations:

```yaml
# verify.yaml
./mvnw clean package -T 1C --no-transfer-progress -Pprod -DskipTests=false ...

# integration-tests.yaml (prepare job)
./mvnw clean package -am --no-transfer-progress -Pprod -DskipTests=true -T 1C ...

# build-only.yaml
./mvnw clean package -T 1C -am --no-transfer-progress -Pprod -DskipTests=true ...

# operator.yaml
mvn -T 1C -B clean package -pl app,distro/docker,operator/olm-tests -am -Pprod -DskipTests ...
```

#### 2. **Redundant Docker Setup** (repeated in 6+ jobs)

Each job repeating:
```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v1
- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v1
- name: Login to DockerHub Registry
  run: echo ${{ secrets.DOCKERHUB_PASSWORD }} | docker login ...
- name: Login to Quay.io Registry
  run: docker login -u "${{ secrets.QUAY_USERNAME }}" ...
```

#### 3. **Duplicated Slack Notifications** (20+ occurrences)

Every job has nearly identical Slack notification blocks:
```yaml
- name: Slack Notification (Always)
  if: github.event_name == 'push' && always()
  run: |
    MESSAGE="'${{ github.workflow }}/${{ github.job }}' job completed..."
    # ... same pattern repeated

- name: Slack Notification (Error)
  if: github.event_name == 'push' && failure()
  run: |
    # ... same pattern repeated
```

#### 4. **verify.yaml Does Too Much**

The `verify.yaml` workflow combines:
- Linting
- Doc generation verification
- Full build with tests
- Multi-arch Docker image builds (3 images)
- UI build and push

This creates a **single point of failure** and long execution times.

#### 5. **Integration Tests: Sequential Execution**

The `integration-tests.yaml` runs multiple test profiles **sequentially within each job**:
```yaml
# H2 job runs 4 separate mvn verify commands sequentially
- name: Run Integration Tests - H2
- name: Run Integration Tests - auth - H2
- name: Run Integration Tests - migration - H2
- name: Run Integration Tests - Debezium
```

#### 6. **Outdated Action Versions**

Multiple workflows use outdated actions:
- `actions/checkout@v2` (should be v4)
- `actions/checkout@v3` (should be v4)
- `actions/upload-artifact@v4.0.0` (should be v4)
- `docker/setup-qemu-action@v1` (should be v3)
- `docker/setup-buildx-action@v1` (should be v3)

#### 7. **No Reusable Workflows**

Each workflow reinvents common patterns instead of using [GitHub reusable workflows](https://docs.github.com/en/actions/using-workflows/reusing-workflows).

#### 8. **Three Separate PR/Push Verification Flows**

- `verify.yaml` - Main code changes
- `integration-tests.yaml` - Also main code changes
- `build-only.yaml` - Docs only changes
- `operator.yaml` - Operator path changes

These could be consolidated using path filtering within a single unified workflow.

---

## Proposed Simplifications

### Phase 1: Create Reusable Workflows (High Impact)

Create `.github/workflows/reusable/` directory with shared workflow components:

#### 1.1. `reusable-build.yaml`
```yaml
name: Reusable Build
on:
  workflow_call:
    inputs:
      skip-tests:
        type: boolean
        default: false
      modules:
        type: string
        default: ""
      profiles:
        type: string
        default: "prod"
    outputs:
      image-tag:
        value: ${{ jobs.build.outputs.image-tag }}

jobs:
  build:
    runs-on: ubuntu-22.04
    outputs:
      image-tag: ${{ github.sha }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 17
          distribution: temurin
          cache: maven

      - name: Build
        run: |
          ./mvnw clean package -T 1C --no-transfer-progress \
            -P${{ inputs.profiles }} \
            -DskipTests=${{ inputs.skip-tests }} \
            ${{ inputs.modules && format('-pl {0} -am', inputs.modules) || '' }}
```

#### 1.2. `reusable-docker.yaml`
```yaml
name: Reusable Docker Build
on:
  workflow_call:
    inputs:
      image-name:
        type: string
        required: true
      dockerfile:
        type: string
        required: true
      context:
        type: string
        required: true
      platforms:
        type: string
        default: "linux/amd64,linux/arm64,linux/s390x,linux/ppc64le"

jobs:
  docker:
    runs-on: ubuntu-22.04
    steps:
      - uses: docker/setup-qemu-action@v3
      - uses: docker/setup-buildx-action@v3

      - name: Login to registries
        run: |
          echo ${{ secrets.DOCKERHUB_PASSWORD }} | docker login -u ${{ secrets.DOCKERHUB_USERNAME }} --password-stdin
          docker login -u "${{ secrets.QUAY_USERNAME }}" -p "${{ secrets.QUAY_PASSWORD }}" quay.io

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          push: true
          file: ${{ inputs.dockerfile }}
          context: ${{ inputs.context }}
          platforms: ${{ inputs.platforms }}
          tags: |
            docker.io/${{ inputs.image-name }}:latest-snapshot
            quay.io/${{ inputs.image-name }}:latest-snapshot
```

#### 1.3. `reusable-notify.yaml`
```yaml
name: Reusable Slack Notification
on:
  workflow_call:
    inputs:
      status:
        type: string
        required: true
      workflow-name:
        type: string
        required: true

jobs:
  notify:
    runs-on: ubuntu-22.04
    if: always()
    steps:
      - name: Send Slack notification
        run: |
          PAYLOAD=$(jq -n \
            --arg workflow "${{ inputs.workflow-name }}" \
            --arg status "${{ inputs.status }}" \
            --arg repo "${{ github.repository }}" \
            --arg link "https://github.com/${{ github.repository }}/actions/runs/${{ github.run_id }}" \
            '{workflow: $workflow, status: $status, repository: $repo, link: $link}')

          curl -X POST -H "Content-Type: application/json" -d "$PAYLOAD" \
            ${{ inputs.status == 'failure' && secrets.SLACK_ERROR_WEBHOOK || secrets.SLACK_NOTIFICATION_WEBHOOK }}
```

---

### Phase 2: Consolidate Verification Workflows

Replace `verify.yaml`, `integration-tests.yaml`, and `build-only.yaml` with a single unified workflow:

#### 2.1. `verify-unified.yaml`
```yaml
name: Unified Verification
on:
  push:
    branches: [main]
    paths-ignore: ['.gitignore', 'LICENSE', 'README*']
  pull_request:
    branches: [main]
    paths-ignore: ['.gitignore', 'LICENSE', 'README*']

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  # Fast checks that should fail early
  lint-and-validate:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v4
      - run: ./scripts/validate-files.sh
      - name: Check docs
        run: |
          if [ -n "$(git status --untracked-files=no --porcelain docs)" ]; then
            echo "Docs need regeneration"
            exit 1
          fi

  # Build once, share artifacts
  build:
    needs: lint-and-validate
    uses: ./.github/workflows/reusable/reusable-build.yaml
    with:
      skip-tests: false
      profiles: prod

  # Build UI in parallel with Java build
  build-ui:
    needs: lint-and-validate
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: ui/**/package-lock.json
      - run: cd ui && npm ci && npm run lint && npm run build && npm run package

  # Integration tests run in parallel matrix
  integration-tests:
    needs: build
    strategy:
      fail-fast: false
      matrix:
        storage: [h2, postgresql, kafkasql]
        profile: [ci, auth, migration]
        exclude:
          - storage: h2
            profile: migration  # H2 doesn't need migration tests
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 17
          distribution: temurin
          cache: maven
      - uses: apicurio/apicurio-github-actions/setup-minikube@v2
      # ... load image and run tests for specific matrix combination
      - run: |
          ./mvnw verify -am --no-transfer-progress \
            -Pintegration-tests -P${{ matrix.profile }} \
            -Premote-${{ matrix.storage == 'h2' && 'mem' || matrix.storage }} \
            -pl integration-tests

  # SDK tests run in parallel
  sdk-tests:
    needs: build
    strategy:
      matrix:
        sdk: [python, go, typescript]
    runs-on: ubuntu-22.04
    # ... SDK-specific test steps

  # Only push images on main branch
  publish-images:
    needs: [build, build-ui, integration-tests]
    if: github.event_name == 'push'
    uses: ./.github/workflows/reusable/reusable-docker.yaml
    # ... image build matrix
```

---

### Phase 3: Optimize Integration Test Execution

#### Current: Sequential test profiles within jobs
```
Job H2:      ci → auth → migration → debezium (sequential)
Job PG:      ci → auth → migration (sequential)
Job Kafka:   ci → auth → migration → snapshotting (sequential)
```

#### Proposed: Matrix-based parallel execution
```
Matrix Jobs (all parallel):
  - h2-ci
  - h2-auth
  - postgresql-ci
  - postgresql-auth
  - postgresql-migration
  - kafkasql-ci
  - kafkasql-auth
  - kafkasql-migration
  - kafkasql-snapshotting
```

**Benefits:**
- Faster overall execution (parallelism)
- Individual test profile failures don't block others
- Better visibility into which specific combinations fail

---

### Phase 4: Update Action Versions

Create a tracking issue or PR to update all actions:

| Current | Target | Locations |
|---------|--------|-----------|
| `actions/checkout@v2` | `@v4` | 1 file |
| `actions/checkout@v3` | `@v4` | 10+ files |
| `actions/setup-java@v3` | `@v4` | 10+ files |
| `actions/setup-node@v3` | `@v4` | 3 files |
| `docker/setup-qemu-action@v1` | `@v3` | 4 files |
| `docker/setup-buildx-action@v1` | `@v3` | 4 files |
| `actions/upload-artifact@v4.0.0` | `@v4` | 5 files |

---

### Phase 5: Consolidate Release Workflows

Currently there are **11 release-related workflows**. Consolidate into:

1. **`release-orchestrator.yaml`** - Main entry point, triggers others
2. **`release-artifacts.yaml`** - Maven artifacts, SBOMs, site
3. **`release-images.yaml`** - All Docker images
4. **`release-sdks.yaml`** - Python, Go, TypeScript SDKs
5. **`release-operator.yaml`** - Kubernetes operator (keep separate due to complexity)

---

## Implementation Roadmap

### Step 1: Create Reusable Workflows
- Create `reusable-build.yaml`
- Create `reusable-docker.yaml`
- Create `reusable-notify.yaml`
- Test with a single workflow first

### Step 2: Consolidate Core Verification
- Create `verify-unified.yaml`
- Migrate `verify.yaml` functionality
- Migrate `integration-tests.yaml` functionality
- Deprecate old workflows (keep as backup initially)

### Step 3: Implement Test Matrix
- Convert sequential test profiles to matrix jobs
- Add fail-fast: false to allow all tests to complete
- Improve test result visibility

### Step 4: Update Actions
- Create single PR updating all action versions
- Test thoroughly in fork first

### Step 5: Consolidate Releases
- Design unified release orchestration
- Implement incrementally per release type

---

## Expected Benefits

| Metric | Current | After | Improvement |
|--------|---------|-------|-------------|
| Workflow files | 27 | ~15 | 44% reduction |
| Duplicated code | High | Minimal | ~70% reduction |
| Integration test time | Sequential | Parallel matrix | ~2-3x faster |
| Maintenance burden | High | Low | Significant |
| Debug visibility | Limited | Per-matrix-cell | Improved |
| Action security | Outdated | Current | Improved |

---

## Risks and Mitigations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Breaking existing CI | Medium | Incremental rollout, keep old workflows as backup |
| Reusable workflow complexity | Low | Start simple, iterate |
| Matrix explosion | Medium | Use `exclude` and careful matrix design |
| Secret handling in reusables | Low | Use `secrets: inherit` or explicit passing |

---

## Quick Wins (Implement First)

1. **Update action versions** - Low risk, security benefit
2. **Extract Slack notifications** - High duplication, easy win
3. **Add `fail-fast: false`** to existing test jobs - Immediate debug improvement
4. **Convert integration tests to matrix** - High impact on execution time

---

## References

- [GitHub Reusable Workflows](https://docs.github.com/en/actions/using-workflows/reusing-workflows)
- [GitHub Actions Matrix](https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs)
- Existing: `docs/parallel-build-plan.md` (complementary document)

---

## Implementation Status

**Status: COMPLETE**

### What Was Implemented

| Phase | Status | Details |
|-------|--------|---------|
| Phase 1: Reusable Workflows | Done | Created `reusable-build.yaml`, `reusable-docker-build.yaml`, `reusable-notify-slack.yaml` (at top level, not in subdirectory) |
| Phase 2: Unified Verification | Done | Created modular `verify.yaml` that orchestrates `verify-*.yaml` sub-workflows; deleted old `integration-tests.yaml`, `build-only.yaml` |
| Phase 3: Matrix Tests | Done | Integration tests now use matrix strategy with 11 parallel jobs |
| Phase 4: Action Updates | Done | All workflows updated to v4/v5 actions |
| Phase 5: Release Consolidation | Done | Created `release-sdks.yaml`, `release-artifacts.yaml`, deleted individual SDK release workflows and redundant workflows |

### Current Workflow Files (28 total)

**Core Verification (orchestrated by `verify.yaml`):**
- `verify.yaml` - Main orchestrator workflow
- `verify-build.yaml` - Java and UI build
- `verify-unit-tests.yaml` - Unit tests
- `verify-integration-tests.yaml` - Matrix-based integration tests
- `verify-extras.yaml` - Additional tests (UI, legacy v2, TypeScript SDK, examples)
- `verify-sdk.yaml` - Python and Go SDK verification
- `verify-native.yaml` - Native image builds
- `verify-publish.yaml` - Docker image publishing (main branch only)

**Reusable Workflows:**
- `reusable-build.yaml` - Configurable Maven build
- `reusable-docker-build.yaml` - Multi-arch Docker build and push
- `reusable-notify-slack.yaml` - Slack notifications

**Releases:**
- `release.yaml` - Main release orchestrator
- `release-images.yaml` - Docker image releases
- `release-artifacts.yaml` - Maven artifacts, SBOMs, site
- `release-sdks.yaml` - Python, Go, TypeScript SDKs
- `release-operator.yaml` - Kubernetes operator
- `release-milestones.yaml` - GitHub milestones
- `release-release-notes.yaml` - Release notes generation

**Automation:**
- `dependabot-autoapprove.yaml` - Auto-approve dependabot PRs
- `dependabot-automerge.yaml` - Auto-merge dependabot PRs

**Validation:**
- `validate-docs.yaml` - Documentation validation
- `validate-openapi.yaml` - OpenAPI spec validation

**Publishing:**
- `publish-docs.yaml` - Publish documentation
- `update-website.yaml` - Update project website
- `update-openapi.yaml` - Update OpenAPI specs

**Scanning:**
- `image-scan.yaml` - Container image security scanning

**Snapshot:**
- `maven-snapshot-release.yaml` - Maven snapshot releases

**Other:**
- `operator.yaml` - Operator verification

### Integration Test Execution

| Before | After |
|--------|-------|
| 3 sequential jobs with multiple sequential test runs each | 1 matrix job with 11 parallel cells |
| H2: ci → auth → migration → debezium (sequential) | All 11 combinations run in parallel |
| PostgreSQL: ci → auth → migration (sequential) | fail-fast: false for better debugging |
| KafkaSQL: ci → auth → migration → snapshotting (sequential) | Individual failure visibility |

**Current Matrix Configuration (from `verify-integration-tests.yaml`):**
```yaml
strategy:
  fail-fast: false
  matrix:
    storage: [h2, postgresql, kafkasql]
    profile: [ci, auth, migration]
    include:
      - storage: h2
        profile: debezium-all
      - storage: kafkasql
        profile: kafkasql-snapshotting
```

### Reusable Workflow Integration

The reusable workflows are at the top level of `.github/workflows/` (not in a subdirectory):

#### `reusable-notify-slack.yaml`
Used by the following workflows:
- `verify.yaml` - Notification after verification completes
- `release-images.yaml` - Notification after image release
- `release-artifacts.yaml` - Notification after artifact releases
- `release-sdks.yaml` - Notification after SDK releases
- `operator.yaml` - Notification after operator publish

#### `reusable-docker-build.yaml`
Enhanced with optional Maven build and artifact download capabilities. Used by:
- `verify-publish.yaml` - Publishing app, UI, MCP server, and native images

Supported features:
- Multi-arch builds (linux/amd64, arm64, s390x, ppc64le)
- Optional Maven build before Docker build (`maven-build: true`)
- Optional artifact download (`download-artifact` parameter)
- Multiple tag support (`additional-tags` parameter)
- Selective registry push (DockerHub and/or Quay.io)

#### `reusable-build.yaml`
Configurable Maven build workflow with options for:
- Java version selection
- Test skipping
- Maven profiles
- Module selection
- Parallel thread configuration
- Artifact upload

### Workflow Architecture

```
verify.yaml (main orchestrator)
├── PHASE 1: lint-and-validate (inline)
├── PHASE 2: build → verify-build.yaml
│   ├── build-java (Java build, creates Docker image artifact)
│   └── build-ui (UI build)
├── PHASE 3: unit-tests → verify-unit-tests.yaml
├── PHASE 4: integration-tests → verify-integration-tests.yaml
│   └── Matrix: 11 parallel jobs (3 storages × 3 profiles + 2 extras)
├── PHASE 5: extras → verify-extras.yaml
│   ├── integration-tests-ui
│   ├── integration-tests-legacy-v2
│   ├── integration-tests-typescript-sdk
│   └── build-examples
├── PHASE 6: sdk → verify-sdk.yaml
│   ├── verify-python-sdk
│   └── verify-go-sdk
├── PHASE 7: native → verify-native.yaml
│   └── build-native-images
├── PHASE 8: publish → verify-publish.yaml (main branch only)
│   ├── publish-app-image → reusable-docker-build.yaml
│   ├── publish-mcp-image → reusable-docker-build.yaml
│   ├── publish-ui-image → reusable-docker-build.yaml
│   └── publish-native-image → reusable-docker-build.yaml
├── PHASE 9: notify-slack → reusable-notify-slack.yaml
└── PHASE 10: trigger-3scale-deploy (external workflow)
```
