# PR Lifecycle

Apicurio Registry uses an automated PR lifecycle orchestrator to manage pull requests.
The orchestrator tracks PR state via labels and provides comment commands for
contributors and maintainers. Test execution itself is controlled natively by two
workflows reacting directly to GitHub events (author identity, review state) — see
[CI below](#continuous-integration).

## Lifecycle Overview

Every non-draft PR moves through these states:

```
Opened --> ready-for-review --> ready-to-merge --> merged
```

There is no separate triage/accept stage — every PR enters `lifecycle/ready-for-review`
as soon as it is opened (or marked ready for review).

| State | Label | What happens |
|-------|-------|--------------|
| **Ready for review** | `lifecycle/ready-for-review` | PR is open and non-draft. The Quick Check fast gate runs on every push. The full verification suite runs immediately for trusted authors (maintainers and configured `auto_accept` identities, e.g. Renovate), or once the PR has an approving review for everyone else. Reviewers can review at any time. |
| **Ready to merge** | `lifecycle/ready-to-merge` | PR is approved and the fast gate passed. Purely a status label — a maintainer can merge it with `/merge` (which enables native GitHub auto-merge; it completes once the full suite and review are both satisfied). |
| **Merged** | — | PR is merged. The branch is deleted automatically (a repository-level GitHub setting, not something the orchestrator does). |

### Draft PRs

The orchestrator ignores draft PRs entirely — no labels, no CI, no welcome message.
Push commits and test locally as normal during draft development. Once you mark the
PR as ready for review (non-draft), it enters `lifecycle/ready-for-review` exactly
like a freshly opened PR.

Converting a PR **back** to draft removes all of its `lifecycle/*` labels and stops
CI from running on subsequent pushes — useful if you need to iterate without burning
CI capacity. Marking it ready for review again re-enters the lifecycle the same way.

### Additional labels

| Label | Meaning |
|-------|---------|
| `lifecycle/tested` | Quick Check fast gate passed for the current HEAD commit. Removed on new pushes. |
| `lifecycle/full-verified` | Full verification suite passed for the current HEAD commit. Removed on new pushes or if the suite subsequently fails. |
| `lifecycle/waiting-on-author` | PR needs action from the author (failed tests or changes requested). |
| `lifecycle/waiting-on-maintainer` | PR needs maintainer attention (ready to review or merge). |
| `lifecycle/stale` | No activity for 4+ days (waiting on author) or 7+ days (otherwise). PR will be closed after further inactivity (see [Stale PRs](#stale-prs)). |
| `ci/disable-scalpel` | Skips the non-blocking `scalpel-report` data-collection job for this PR. |

## For Contributors

### Opening a PR

1. Open a PR against `main` (draft or regular)
   - Draft PRs are ignored by the orchestrator — push and test locally as you go
2. When ready (or if opened as non-draft), the orchestrator posts a welcome message
   and the PR enters `lifecycle/ready-for-review`
3. The Quick Check fast gate runs immediately; the full verification suite runs once
   a maintainer approves the PR (no `/accept` step needed)

### After review

- If changes are requested, push fixes. The fast gate re-runs automatically, and the
  full suite re-runs once re-approved (GitHub dismisses stale approvals on new pushes).
- Once approved and the fast gate passes, the PR moves to `ready-to-merge`
- A maintainer will merge it, or enable auto-merge with `/merge` so it merges
  automatically once the full suite finishes

### Stale PRs

If your PR has no activity for 7 days, it will be marked as stale and you will be
pinged. Comment or push to remove the stale label, or use `/unstale`. PRs blocked on
you (`lifecycle/waiting-on-author`) go stale sooner — after 4 days — and are closed
after 7 total days of inactivity; other PRs go stale at 7 days and close at 14 total.

### Available commands

| Command | Description |
|---------|-------------|
| `/unstale` | Remove the stale label |
| `/retry` | Re-run the lifecycle orchestrator and retry failed tests |
| `/assign-me` | Self-assign an open issue to volunteer for implementation |
| `/unassign-me` | Release an issue you are currently assigned to |

### Issue Self-Assignment

Contributors can self-assign open issues by commenting `/assign-me` (or `/claim`).

- **Assignment Limit**: Each contributor can have a maximum of 3 open issues assigned concurrently.
- **Unassigning**: Comment `/unassign-me` to release an issue.
- **Overriding**: Maintainers can override assignments directly via the GitHub UI at any time.


## For Maintainers

### Managing the lifecycle

| Command | Description |
|---------|-------------|
| `/reject [reason]` | Close a PR that should not be worked further |
| `/merge` | Toggle native GitHub auto-merge — it merges automatically once required checks pass and the PR has an approving review |
| `/unstale` | Remove the stale label |
| `/retry` | Re-run the lifecycle orchestrator and retry failed tests |

There is no `/accept` or `/skip-review` command any more: there is no triage stage to
accept a PR into, and every author needs an actual approving review before a PR can
merge — required natively by branch protection (`required_approving_review_count: 1`),
not something a maintainer can waive per PR.

### Merge strategy

PRs are merged using **rebase** by default (linear history via `/merge`, which enables
native GitHub auto-merge). This can be changed to **squash** in `.github/pr-lifecycle.yml`.
Branches are deleted automatically after merge (a repository-level setting).

### Label protection

All `lifecycle/*` and `orchestrator/*` labels are managed exclusively by the orchestrator.
Manual label changes will be reverted automatically. Use the appropriate slash command
instead of adding or removing labels directly.

### Branches that fall behind `main`

Branch protection requires PR branches to be up to date before merging. Native GitHub
auto-merge (enabled via `/merge`) waits correctly in that case, but it does not update
the branch for you — if a PR has been open a while and other PRs merged in the
meantime, click **Update branch** on the PR page (or push a rebase) to let it proceed.

## Continuous Integration

Two workflows make up the whole pipeline — see `.github/workflows/README.md` for the
full technical description. In short:

- **`quick-check.yaml`** — the fast gate. Runs on every push to every PR, regardless
  of author or review state.
- **`verify.yaml`** — the full suite (build, unit tests, CLI, SDKs, console plugin,
  integration tests, extra tests, operator tests). Its `decide` job evaluates, live,
  on every run:
  - author is a maintainer or in `auto_accept` (e.g. Renovate) → runs immediately
  - otherwise → runs once the PR has a current approving review (`reviewDecision ==
    APPROVED`), re-evaluated automatically on every review submission
  - `orchestrator/disabled` label → runs regardless (unless `DO NOT MERGE` is also
    present), the legacy escape hatch for PRs excluded from the lifecycle entirely

This is a native-fact decision (author identity, review state), not a bot-applied
label — there is nothing for the orchestrator to keep in sync, and no window where a
run started before a promotion could go stale relative to it.

## Configuration

The orchestrator is configured in `.github/pr-lifecycle.yml`:

- **maintainers** — GitHub usernames of maintainers (controls who can use maintainer
  commands, and who gets the full suite immediately per the CI section above)
- **auto_accept** — GitHub usernames of additional trusted accounts that also get the
  full suite immediately (e.g. Renovate), without maintainer command access
- **max_contributor_prs** — maximum concurrent open PRs for a non-trusted author
  before further ones are closed automatically (default: 1)
- **merge.strategy** — `rebase` (default) or `squash`
- **stale.days_until_stale** — days of inactivity before marking as stale (default: 7)
- **stale.days_until_close** — total days of inactivity before closing (default: 14)
- **stale.days_until_stale_waiting_on_author** — days of inactivity before marking a PR
  blocked on the author as stale (default: 4)
- **stale.days_until_close_waiting_on_author** — total days of inactivity before closing a
  PR blocked on the author (default: 7)
- **welcome_message** — message posted when a PR is opened

## Disabling the Orchestrator

The orchestrator is enabled by default on all PRs. To exclude a specific PR, a maintainer
can add the `orchestrator/disabled` label. This reverts the PR to legacy behavior (full
test suite on every push, `DO NOT MERGE` label support).
</content>
