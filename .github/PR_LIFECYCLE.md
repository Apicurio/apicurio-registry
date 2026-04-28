# PR Lifecycle

Apicurio Registry uses an automated PR lifecycle orchestrator to manage pull requests.
The orchestrator controls test execution, tracks PR state via labels, and provides
comment commands for contributors and maintainers.

## Lifecycle Overview

Every PR moves through these states:

```
Opened --> new --> wip --> ready-for-review --> ready-to-merge --> merged
```

| State | Label | What happens |
|-------|-------|--------------|
| **New** | `lifecycle/new` | PR just opened. A welcome message is posted. No tests run. A maintainer must triage. PRs from maintainers and trusted accounts (e.g., Renovate) skip this state. |
| **WIP** | `lifecycle/wip` | Maintainer accepted the PR (or auto-accepted for trusted authors). Smoke tests (lint, build, unit tests) run on each push. |
| **Ready for review** | `lifecycle/ready-for-review` | Author marked the PR as ready. Full test suite runs. Reviewers can review. |
| **Ready to merge** | `lifecycle/ready-to-merge` | PR is approved and all tests pass. A maintainer can merge. |
| **Merged** | — | PR is merged. Branch may be deleted automatically. |

### Additional labels

| Label | Meaning |
|-------|---------|
| `lifecycle/tested` | Full test suite passed for the current HEAD commit. Removed on new pushes. |
| `lifecycle/waiting-on-author` | PR needs action from the author (failed tests or changes requested). |
| `lifecycle/waiting-on-maintainer` | PR needs maintainer attention (ready to review or merge). |
| `lifecycle/stale` | No activity for 7 days. PR will be closed after 7 more days of inactivity. |

## For Contributors

### Opening a PR

1. Open a PR against `main` (draft or regular — both work the same way)
2. The orchestrator posts a welcome message and adds `lifecycle/new`
3. A maintainer will review and accept your PR with `/accept`

### During development (WIP)

- Push commits as normal. Smoke tests run automatically (lint, build, unit tests)
- If you want to disable smoke tests (e.g., documentation PR), comment `/disable-tests`
- To re-enable, comment `/enable-tests`

### When ready for review

1. Make sure a maintainer has assigned at least one reviewer
2. Comment `/ready` on the PR (or convert from draft to ready if using draft PRs)
3. The full test suite runs automatically
4. Wait for review and test results

### After review

- If changes are requested, push fixes. Tests re-run automatically.
- Once approved and tests pass, the PR moves to `ready-to-merge`
- A maintainer will merge it, or it merges automatically if auto-merge is enabled

### Stale PRs

If your PR has no activity for 7 days, it will be marked as stale. You will be
pinged. Comment or push to remove the stale label, or use `/unstale`. After 14
total days of inactivity, the PR will be closed automatically.

### Available commands

| Command | Description |
|---------|-------------|
| `/ready` | Mark your PR as ready for review |
| `/disable-tests` | Disable smoke tests during WIP |
| `/enable-tests` | Re-enable smoke tests |
| `/unstale` | Remove the stale label |

## For Maintainers

### Triaging new PRs

When a new PR arrives (`lifecycle/new`):
1. Review the PR description and scope
2. Assign a reviewer
3. Accept with `/accept` or reject with `/reject [reason]`

### Managing the lifecycle

| Command | Description |
|---------|-------------|
| `/accept` | Accept a new PR, transition to WIP |
| `/reject [reason]` | Reject and close a new PR |
| `/ready` | Mark a WIP PR as ready for review (can also be done by the author) |
| `/merge` | Merge a PR that is in `ready-to-merge` state |
| `/auto-merge` | Toggle auto-merge (PR merges automatically when ready-to-merge is reached) |
| `/disable-tests` | Disable smoke tests for a WIP PR |
| `/enable-tests` | Re-enable smoke tests |
| `/unstale` | Remove the stale label |

### Merge strategy

PRs are merged using **rebase** by default (linear history). This can be changed to
**squash** in `.github/pr-lifecycle.yml`. Branches are automatically deleted after merge.

### Label protection

All `lifecycle/*` and `orchestrator/*` labels are managed exclusively by the orchestrator.
Manual label changes will be reverted automatically. Use the appropriate slash command
instead of adding or removing labels directly.

### Auto-merge

Use `/auto-merge` to enable automatic merging. When the PR reaches `ready-to-merge`
(approved + tested), it will be merged automatically. Use `/auto-merge` again to disable.

## Configuration

The orchestrator is configured in `.github/pr-lifecycle.yml`:

- **maintainers** — GitHub usernames of maintainers (controls who can use maintainer commands)
- **auto_accept** — GitHub usernames of accounts that skip triage (auto-accepted to WIP). Maintainers are always auto-accepted.
- **merge.strategy** — `rebase` (default) or `squash`
- **merge.delete_branch** — whether to delete the branch after merge
- **stale.days_until_stale** — days of inactivity before marking as stale (default: 7)
- **stale.days_until_close** — total days of inactivity before closing (default: 14)
- **welcome_message** — message posted when a PR is opened
- **smoke_tests.enabled_by_default** — whether smoke tests run by default in WIP state

## Test Gating

The orchestrator controls which tests run at each lifecycle stage:

| State | Tests |
|-------|-------|
| `lifecycle/new` | None |
| `lifecycle/wip` | Smoke: lint, build, unit tests |
| `lifecycle/ready-for-review` | Full suite: lint, build, unit tests, integration tests, SDK tests, extras |
| No orchestrator label | Full suite (backward compatible with non-orchestrated PRs) |

## Opt-in Mode

During rollout, the orchestrator only manages PRs with the `orchestrator/enabled` label.
PRs without this label use the existing workflow (full test suite on every push).
