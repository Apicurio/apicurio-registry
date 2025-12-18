# Cross-Repository Testing Integration Plan

## Executive Summary

This document outlines a plan to trigger integration tests in the `apicurio-testing` repository when new commits are pushed to the `main` branch of `apicurio-registry`. This enables comprehensive testing against OpenShift clusters and additional infrastructure that isn't available in the standard GitHub Actions runners.

---

## Current State Analysis

### apicurio-registry Workflows

The registry has two main workflows triggered on pushes to `main`:

| Workflow | Purpose | Current Behavior on Push to Main |
|----------|---------|----------------------------------|
| `verify.yaml` | Build, test, push snapshot images | Builds and pushes `latest-snapshot` images to DockerHub and Quay.io |
| `integration-tests.yaml` | Run integration tests | Matrix-based tests against H2, PostgreSQL, KafkaSQL |

**Key Observation**: The `verify.yaml` workflow already has a `trigger-3scale-deploy` job (line 397-401) that demonstrates cross-repository workflow dispatch:

```yaml
trigger-3scale-deploy:
  needs: [build-verify, build-verify-ui]
  if: github.event_name == 'push'
  uses: apicurio/apicurio-3scale-gitops/.github/workflows/deploy_latest_registry.yml@main
  secrets: inherit
```

### apicurio-testing Repository

The testing repository contains several workflows:

| Workflow | Purpose | Trigger |
|----------|---------|---------|
| `test-registry-release.yaml` | Full release testing | `workflow_dispatch`, `workflow_call` |
| `wrapper-registry-release.yaml` | Wrapper for release testing | `workflow_dispatch` |
| `provision-cluster.yaml` | Provision OpenShift cluster | (needs investigation) |
| `test-registry-operator.yaml` | Operator testing | (needs investigation) |

**`test-registry-release.yaml` Inputs:**
- `releaseVersion` (required): Version to test
- `appImage` (optional): Custom application image
- `uiImage` (optional): Custom UI image
- `operatorImage` (optional): Custom operator image
- `testsTag` (default: 'main'): Git tag for test scripts
- `isDownstream` (default: false): Test downstream images
- `skipClusterInstall` (default: false): Skip cluster setup
- `dryRun` (optional): Skip committing results

---

## Proposed Solution

### Option 1: Direct Workflow Call (Recommended)

Add a new job to `verify.yaml` that calls the apicurio-testing workflow after images are pushed:

```yaml
# Add to verify.yaml after the trigger-3scale-deploy job

trigger-extended-testing:
  name: Trigger Extended Testing
  needs: [build-verify, build-verify-ui]
  if: github.event_name == 'push' && github.ref == 'refs/heads/main'
  uses: Apicurio/apicurio-testing/.github/workflows/test-registry-release.yaml@main
  with:
    releaseVersion: 'latest-snapshot'
    appImage: 'quay.io/apicurio/apicurio-registry:latest-snapshot'
    uiImage: 'quay.io/apicurio/apicurio-registry-ui:latest-snapshot'
    testsTag: 'main'
    skipClusterInstall: false
    dryRun: false
  secrets: inherit
```

**Advantages:**
- Direct integration, follows existing pattern (`trigger-3scale-deploy`)
- Type-safe inputs with validation
- Secrets automatically inherited
- Visible in GitHub Actions UI as a connected workflow
- No additional infrastructure needed

**Disadvantages:**
- Requires `workflow_call` trigger in apicurio-testing (already present)
- Registry workflow must wait for extended tests to complete (can be mitigated)

---

### Option 2: Repository Dispatch (Decoupled)

Use GitHub's `repository_dispatch` event for a more decoupled approach:

**In apicurio-registry `verify.yaml`:**

```yaml
trigger-extended-testing:
  name: Trigger Extended Testing
  needs: [build-verify, build-verify-ui]
  if: github.event_name == 'push' && github.ref == 'refs/heads/main'
  runs-on: ubuntu-22.04
  steps:
    - name: Trigger apicurio-testing workflow
      uses: peter-evans/repository-dispatch@v3
      with:
        token: ${{ secrets.APICURIO_CI_TOKEN }}
        repository: Apicurio/apicurio-testing
        event-type: registry-snapshot-build
        client-payload: |
          {
            "ref": "${{ github.sha }}",
            "appImage": "quay.io/apicurio/apicurio-registry:latest-snapshot",
            "uiImage": "quay.io/apicurio/apicurio-registry-ui:latest-snapshot",
            "triggerRepo": "apicurio-registry",
            "triggerWorkflow": "${{ github.workflow }}",
            "triggerRunId": "${{ github.run_id }}"
          }
```

**In apicurio-testing (new or modified workflow):**

```yaml
name: Snapshot Testing

on:
  repository_dispatch:
    types: [registry-snapshot-build]

jobs:
  test-snapshot:
    runs-on: ubuntu-22.04
    steps:
      - name: Log trigger information
        run: |
          echo "Triggered by: ${{ github.event.client_payload.triggerRepo }}"
          echo "Commit SHA: ${{ github.event.client_payload.ref }}"
          echo "App Image: ${{ github.event.client_payload.appImage }}"

      # ... rest of test steps or call existing workflow
```

**Advantages:**
- Completely decoupled - registry workflow completes immediately
- Can pass arbitrary payload data
- Easy to add additional trigger sources
- Testing repository can evolve independently

**Disadvantages:**
- Requires a Personal Access Token (PAT) or GitHub App token with repo scope
- Less visible in GitHub Actions UI
- Additional workflow file in apicurio-testing needed

---

### Option 3: Scheduled + On-Demand Hybrid

Keep testing scheduled but add on-demand triggering:

**In apicurio-testing, create `test-snapshot.yaml`:**

```yaml
name: Test Latest Snapshot

on:
  schedule:
    # Run every 6 hours to catch main branch changes
    - cron: '0 */6 * * *'
  workflow_dispatch:
    inputs:
      appImage:
        description: 'Registry app image to test'
        required: false
        default: 'quay.io/apicurio/apicurio-registry:latest-snapshot'
      uiImage:
        description: 'Registry UI image to test'
        required: false
        default: 'quay.io/apicurio/apicurio-registry-ui:latest-snapshot'
  repository_dispatch:
    types: [registry-snapshot-build]

jobs:
  test:
    uses: ./.github/workflows/test-registry-release.yaml
    with:
      releaseVersion: 'latest-snapshot'
      appImage: ${{ github.event.inputs.appImage || github.event.client_payload.appImage || 'quay.io/apicurio/apicurio-registry:latest-snapshot' }}
      uiImage: ${{ github.event.inputs.uiImage || github.event.client_payload.uiImage || 'quay.io/apicurio/apicurio-registry-ui:latest-snapshot' }}
      testsTag: 'main'
      skipClusterInstall: false
    secrets: inherit
```

---

## Recommended Implementation: Option 1 with Async Pattern

The recommended approach combines Option 1's simplicity with an async pattern to avoid blocking:

### Step 1: Modify verify.yaml

Add a new job after the image push jobs:

```yaml
# In verify.yaml, add after trigger-3scale-deploy (around line 402)

trigger-extended-testing:
  name: Trigger Extended Integration Tests
  needs: [build-verify, build-verify-ui]
  if: github.event_name == 'push' && github.ref == 'refs/heads/main'
  uses: Apicurio/apicurio-testing/.github/workflows/test-registry-release.yaml@main
  with:
    releaseVersion: 'snapshot-${{ github.sha }}'
    appImage: 'quay.io/apicurio/apicurio-registry:latest-snapshot'
    uiImage: 'quay.io/apicurio/apicurio-registry-ui:latest-snapshot'
    testsTag: 'main'
    skipClusterInstall: false
    dryRun: false
  secrets: inherit
```

### Step 2: Verify apicurio-testing Workflow Supports workflow_call

Ensure `test-registry-release.yaml` in apicurio-testing has the `workflow_call` trigger (already confirmed present).

### Step 3: Handle Secrets

The `secrets: inherit` directive passes all secrets from the calling workflow. Ensure the required secrets exist in the registry repository or are organization-level secrets:

| Secret | Purpose |
|--------|---------|
| `OPENSHIFT_SERVER` | OpenShift cluster URL |
| `OPENSHIFT_TOKEN` | OpenShift API token |
| Other cluster-specific secrets | As needed by testing scripts |

---

## Implementation Checklist

### Phase 1: Preparation

- [ ] Audit secrets required by apicurio-testing workflows
- [ ] Verify organization-level secrets are accessible
- [ ] Test `test-registry-release.yaml` manually with `workflow_dispatch`

### Phase 2: Implementation

- [ ] Add `trigger-extended-testing` job to `verify.yaml`
- [ ] Test with a PR to main (dry run)
- [ ] Merge and verify end-to-end flow

### Phase 3: Monitoring & Notification

- [ ] Add Slack notification for extended test results
- [ ] Create dashboard or badge for test status
- [ ] Document the testing flow in CLAUDE.md

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        apicurio-registry (main branch push)              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐  │
│  │  build-verify    │    │  build-verify-ui │    │  build-native    │  │
│  │  (Java build +   │    │  (UI build +     │    │  (Native image)  │  │
│  │   push images)   │    │   push images)   │    │                  │  │
│  └────────┬─────────┘    └────────┬─────────┘    └──────────────────┘  │
│           │                       │                                      │
│           └───────────┬───────────┘                                      │
│                       │                                                  │
│                       ▼                                                  │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    needs: [build-verify, build-verify-ui]          │ │
│  ├────────────────────────────────────────────────────────────────────┤ │
│  │  ┌─────────────────────┐    ┌──────────────────────────────────┐  │ │
│  │  │ trigger-3scale-     │    │ trigger-extended-testing         │  │ │
│  │  │ deploy              │    │ (NEW)                            │  │ │
│  │  │ ─────────────────── │    │ ──────────────────────────────── │  │ │
│  │  │ uses: apicurio-     │    │ uses: Apicurio/apicurio-testing/ │  │ │
│  │  │ 3scale-gitops/...   │    │ .github/workflows/test-registry- │  │ │
│  │  │                     │    │ release.yaml@main                │  │ │
│  │  └──────────┬──────────┘    └───────────────┬──────────────────┘  │ │
│  └─────────────┼───────────────────────────────┼──────────────────────┘ │
│                │                               │                         │
└────────────────┼───────────────────────────────┼─────────────────────────┘
                 │                               │
                 ▼                               ▼
┌────────────────────────────┐    ┌────────────────────────────────────────┐
│   apicurio-3scale-gitops   │    │         apicurio-testing               │
│   ──────────────────────   │    │         ────────────────               │
│   Deploy to 3Scale         │    │   ┌──────────────────────────────────┐ │
│   OpenShift cluster        │    │   │  test-registry-release.yaml     │ │
│                            │    │   │  ────────────────────────────── │ │
└────────────────────────────┘    │   │  - Provision OpenShift cluster  │ │
                                  │   │  - Deploy Registry + Operator   │ │
                                  │   │  - Run integration tests        │ │
                                  │   │  - Run UI tests                 │ │
                                  │   │  - Run DAST scans               │ │
                                  │   │  - Report results               │ │
                                  │   └──────────────────────────────────┘ │
                                  └────────────────────────────────────────┘
```

---

## Fallback Options

If the direct workflow call approach has issues:

1. **Repository Dispatch**: Use Option 2 with a PAT stored as a secret
2. **Scheduled Testing**: Run apicurio-testing on a schedule (every 4-6 hours)
3. **Manual Trigger**: Add a comment-based trigger (e.g., `/test-extended`)

---

## Security Considerations

1. **Secrets Access**: Using `secrets: inherit` requires trust between repositories
2. **Branch Protection**: Ensure only protected branches can trigger cross-repo workflows
3. **Token Scope**: If using repository_dispatch, use minimum required permissions
4. **Audit Trail**: All workflow runs are logged in GitHub Actions

---

## Metrics & Monitoring

### Suggested Metrics

- Extended test pass rate over time
- Time to detect regressions
- Average test execution duration
- Cluster provisioning reliability

### Integration with Existing Notifications

Extend the existing Slack notification pattern:

```yaml
- name: Slack Notification (Extended Tests)
  if: always()
  run: |
    STATUS="${{ job.status }}"
    MESSAGE="Extended tests for commit ${{ github.sha }} completed with status: $STATUS"
    # ... notification payload
```

---

## References

- [GitHub Reusable Workflows](https://docs.github.com/en/actions/using-workflows/reusing-workflows)
- [GitHub Repository Dispatch](https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows#repository_dispatch)
- Existing pattern: `verify.yaml` line 397-401 (`trigger-3scale-deploy`)
- [apicurio-testing repository](https://github.com/Apicurio/apicurio-testing)
