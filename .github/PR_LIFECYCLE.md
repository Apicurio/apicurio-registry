# PR Lifecycle

Apicurio Registry uses an automated PR lifecycle orchestrator to manage pull requests.
The orchestrator controls test execution, tracks PR state via labels, and provides
comment commands for contributors and maintainers.

## Lifecycle Overview

Every non-draft PR moves through these states:

```
Opened --> new --> ready-for-review --> ready-to-merge --> merged
```

| State | Label | What happens |
|-------|-------|--------------|
| **New** | `lifecycle/new` | PR just opened. A welcome message is posted. No tests run. A maintainer must triage. PRs from maintainers and trusted accounts (e.g., Renovate) skip this state. |
| **Ready for review** | `lifecycle/ready-for-review` | Maintainer accepted the PR (or auto-accepted for trusted authors). The full test suite runs on each push. Reviewers can review. |
| **Ready to merge** | `lifecycle/ready-to-merge` | PR is approved and all tests pass. A maintainer can merge. |
| **Merged** | — | PR is merged. Branch may be deleted automatically. |

### Draft PRs

The orchestrator ignores draft PRs entirely — no labels, no CI, no welcome message.
Push commits and test locally as normal during draft development. Once you mark the
PR as ready for review (non-draft), it enters `lifecycle/new` exactly like a freshly
opened PR.

Converting a PR **back** to draft removes all of its `lifecycle/*` labels and stops
CI from running on subsequent pushes — useful if you need to iterate without burning
CI capacity. Marking it ready for review again re-enters the lifecycle at
`lifecycle/new`, so a maintainer has to `/accept` it a second time.

### Additional labels

| Label | Meaning |
|-------|---------|
| `lifecycle/tested` | Full test suite passed for the current HEAD commit. Removed on new pushes. |
| `lifecycle/review-approved` | PR has an approved review. Removed on new pushes or when changes are requested. |
| `lifecycle/waiting-on-author` | PR needs action from the author (failed tests or changes requested). |
| `lifecycle/waiting-on-maintainer` | PR needs maintainer attention (ready to review or merge). |
| `lifecycle/stale` | No activity for 4+ days (waiting on author) or 7+ days (otherwise). PR will be closed after further inactivity (see [Stale PRs](#stale-prs)). |
| `ci/disable-scalpel` | Skips the non-blocking `scalpel-report` data-collection job for this PR. |

## For Contributors

### Opening a PR

1. Open a PR against `main` (draft or regular)
   - Draft PRs are ignored by the orchestrator — push and test locally as you go
2. When ready (or if opened as non-draft), the orchestrator posts a welcome message
   and adds `lifecycle/new`
3. A maintainer will review and accept your PR with `/accept`

### After acceptance

- `/accept` moves the PR directly to `lifecycle/ready-for-review` and runs the full
  test suite
- Push commits as normal; the full suite re-runs on each push
- Wait for review and test results

### After review

- If changes are requested, push fixes. Tests re-run automatically.
- Once approved and tests pass, the PR moves to `ready-to-merge`
- A maintainer will merge it, or it merges automatically if auto-merge is enabled

### Stale PRs

If your PR has no activity for 7 days, it will be marked as stale and you will be
pinged. Comment or push to remove the stale label, or use `/unstale`. PRs blocked on
you (`lifecycle/waiting-on-author`) go stale sooner — after 4 days — and are closed
after 7 total days of inactivity; other PRs go stale at 7 days and close at 14 total.

### Available commands

| Command | Description |
|---------|-------------|
| `/unstale` | Remove the stale label |
| `/assign-me` | Self-assign an open issue to volunteer for implementation |
| `/unassign-me` | Release an issue you are currently assigned to |

### Issue Self-Assignment

Contributors can self-assign open issues by commenting `/assign-me` (or `/claim`).

- **Assignment Limit**: Each contributor can have a maximum of 3 open issues assigned concurrently.
- **Unassigning**: Comment `/unassign-me` to release an issue.
- **Overriding**: Maintainers can override assignments directly via the GitHub UI at any time.


## For Maintainers

### Triaging new PRs

When a new PR arrives (`lifecycle/new`):
1. Review the PR description and scope
2. Accept with `/accept` (transitions directly to `ready-for-review`, full test
   suite runs) or reject with `/reject [reason]`

### Managing the lifecycle

| Command | Description |
|---------|-------------|
| `/accept` | Accept a new PR, transition to `ready-for-review` and run the full test suite |
| `/reject [reason]` | Reject and close a new PR |
| `/skip-review` | Skip the review requirement for small changes (tests still required) |
| `/merge` | Merge a PR that is in `ready-to-merge` state |
| `/auto-merge` | Toggle auto-merge (PR merges automatically when ready-to-merge is reached) |
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

### Branch auto-update (merge-rebase)

When merging fails because the PR branch is behind `main` (other PRs were merged in the
meantime), the orchestrator handles it automatically:

1. If the branch can be cleanly rebased (`rebaseable: true`), the branch is updated
2. Tests are **skipped** — they already passed on the previous HEAD, and a clean rebase
   means the code is functionally identical
3. Only the Verification Gate runs (~1-2 minutes)
4. Once the gate passes, the merge proceeds automatically

If the branch has conflicts, the orchestrator posts an error and asks the author to
resolve them manually. If the branch falls behind again during the gate run, the
merge falls back to standard error handling (full test suite re-run).

## Configuration

The orchestrator is configured in `.github/pr-lifecycle.yml`:

- **maintainers** — GitHub usernames of maintainers (controls who can use maintainer commands)
- **auto_accept** — GitHub usernames of accounts that skip triage (auto-accepted directly to
  `ready-for-review`). Maintainers are always auto-accepted.
- **merge.strategy** — `rebase` (default) or `squash`
- **merge.delete_branch** — whether to delete the branch after merge
- **stale.days_until_stale** — days of inactivity before marking as stale (default: 7)
- **stale.days_until_close** — total days of inactivity before closing (default: 14)
- **stale.days_until_stale_waiting_on_author** — days of inactivity before marking a PR
  blocked on the author as stale (default: 4)
- **stale.days_until_close_waiting_on_author** — total days of inactivity before closing a
  PR blocked on the author (default: 7)
- **welcome_message** — message posted when a PR is opened

## Test Gating

The orchestrator controls which tests run at each lifecycle stage:

| State | Tests |
|-------|-------|
| `lifecycle/new` | None |
| `lifecycle/ready-for-review` | Full suite: lint, build, unit tests, integration tests, SDK tests, extras |
| `orchestrator/disabled` | Full suite (legacy behavior, DO NOT MERGE still works) |

## Disabling the Orchestrator

The orchestrator is enabled by default on all PRs. To exclude a specific PR, a maintainer
can add the `orchestrator/disabled` label. This reverts the PR to legacy behavior (full
test suite on every push, `DO NOT MERGE` label support).
