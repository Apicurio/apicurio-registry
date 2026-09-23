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
| `lifecycle/stale` | No activity for 4+ days (waiting on author) or 7+ days (otherwise). PR will be closed after further inactivity (see [Stale PRs](#stale-prs)). Never applied to a PR blocked on a maintainer. |
| `lifecycle/review-overdue` | Blocked on a maintainer for 14+ days. Purely a visibility signal for us — it never leads to the PR being closed (see [Stale PRs](#stale-prs)). |
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

The stale timers measure **contributor** inactivity, so they only run while the ball is
in your court. If your PR has no activity for 7 days it is marked stale and you are
pinged; comment or push to clear the label, or use `/unstale`. PRs blocked on you
(`lifecycle/waiting-on-author`) go stale sooner — after 4 days — and close after 7 total
days of inactivity; other PRs go stale at 7 days and close at 14 total.

**A PR waiting on us never goes stale and is never auto-closed.** While it carries
`lifecycle/waiting-on-maintainer` there is nothing for you to do, so no timer runs
against you and no inactivity warning is addressed to you. If it stays blocked on us for
14 days it gets `lifecycle/review-overdue`, and after 30 days the orchestrator pings the
assignees and reviewers — the PR is still not at risk, that label exists to hold *us* to
account for the review backlog, not you.

If a PR is blocked on both you and a maintainer, the contributor timer wins, so it can
still go stale.

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
- **Maintainer-only labels**: Issues carrying one of `MAINTAINER_ONLY_LABELS` in
  `.github/scripts/issue-assignment.js` (currently `area/CI`, including labels nested under it such as
  `area/CI/automation`) cannot be self-assigned — the bot
  declines and asks for a different issue to be picked. Maintainers (the list in
  `.github/pr-lifecycle.yml`) are exempt. Labels are applied automatically, so a contributor who
  believes the label does not fit, or who wants an exception, can ask in a comment — the bot
  points them at the reporter if that reporter is a maintainer, and at `@Apicurio/maintainers`
  otherwise. A maintainer then either removes the label or assigns the issue by hand.


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

### Reviewer assignment

When a PR opens or leaves draft, the `classify-pr` job in `.github/workflows/classify.yml`
gives it `area/*` labels and then picks a reviewer
(`.github/scripts/pr-reviewer-assignment.js`):

| PR author | What happens |
|-----------|--------------|
| Contributor | The winner is **assigned**, and a comment says so in one line, with the reasoning in a collapsed section (see [Reading the comment](#reading-the-comment)). |
| Maintainer | The same scoring, posted as a **suggestion** only. Nobody is assigned or @-mentioned; you request the review yourself. |
| Dependency bot (`auto_accept`, i.e. Renovate) | **Rotated** to whichever `bot_rotation` reviewer has had the fewest bot PRs in the last 30 days. No comment. |

It runs once per PR. It skips a PR it has already commented on, and also:
- a contributor or Renovate PR that already has an assignee;
- a maintainer PR that already has a requested reviewer, or an assignee other than its
  author. Assigning yourself to your own PR doesn't count: that's ownership, not review.

Reassigning in the GitHub UI is always fine; the bot will not undo it.

#### How a reviewer is picked

**1. Who is considered.** Everyone in `reviewer_assignment.reviewers` except the PR author,
provided they have **some link to the PR**: interest above 0 in one of its areas, a recent
commit to one of its files, or authorship of an issue it closes. If nobody has a link,
everyone is considered.

**2. The score.** Three signals, each 0–1, weighted (`reviewer_assignment.weights`):

| Signal | Weight | What it measures |
|--------|--------|------------------|
| Git ownership | 0.55 | Your share, **among the reviewers**, of the recent commits to the files the PR changes: the 10 most recent commits per file within 12 months, commits from the last 3 months counting double. Contributors' commits don't dilute it: if you're the only reviewer who has touched a file, it's all yours. A file no reviewer has touched counts as 0 for everyone. Each file weighs the same, so one file with a long history cannot outvote the rest. Only the 30 largest changed files are read, and lockfiles and generated SDK code are skipped. |
| Interest | 0.30 | Your **highest** weight among the PR's area labels, from your interest map (below). |
| Issue author | 0.15 | 1 if you opened an issue the PR closes. |

**3. The swap.** Your **load** is the number of PRs you were assigned in the last 30
days, in any state: merged and closed count too, so clearing your queue doesn't count
against you. Assignments from the last 48 hours count double, and bot PRs are counted
separately. Load isn't part of the score. It does two things:
- **Breaks exact ties**, in favor of the less loaded reviewer.
- **Swaps an overloaded winner.** If the winner's load is at least **1.5×** the
  runner-up's and at least **3 more**, the runner-up gets the PR, however far behind on
  score. This is what keeps git ownership from piling PRs on whoever commits most:
  without it, one maintainer got 43% of contributor PRs in the backtest.

The swap only goes to a runner-up with interest in the PR's **primary area**, the label
the classifier was most confident about. Specialists always look idle, because they
cover few areas, not because they're free. Without this condition the swap handed them
PRs whose only link to their area was an incidental label, like a UI PR that touched
docs going to the docs reviewer. A PR with no area labels can be swapped to anyone.
Measuring load against how many PRs each reviewer was eligible for, instead of raw counts,
is tracked in #10247.

#### Reading the comment

The contributor sees one line (`Auto-assigned to @…`). The collapsed **Why this
reviewer** section is for tuning:
- **A table of the reviewers who were considered.** It shows each one's score, the raw
  ownership, interest and issue-author values (the column headers carry the weights), and
  their load. The assignee is in bold. Comparing the table with the config usually tells
  you which knob to turn.
- **The PR's areas, with the primary one marked.** If they're wrong, the labels are the
  problem, not the interest maps (see knob 2 below).
- **Swapped / Not swapped:** whether an overloaded winner handed the PR on, or kept it
  because the runner-up has no interest in the primary area.
- **Not considered:** reviewers with no link to the PR at all.
- **Links** to this section and to the workflow run.

The run log has the same numbers plus the weighted parts of every score. To see them
for any open PR without assigning anything, run the **Classify** workflow manually with
the PR number and `dry_run` checked. This works on PRs the bot has already handled: the
log notes that a live run would skip it, and scores it anyway. A dry run scores with
today's history and load, so it can differ from the original decision; the comment is
the record of that.

#### Adjusting which PRs are assigned to you

There are two knobs you control directly: your **interest map** and the **area labels**
it reads.

**1. Your interest map** — `reviewer_assignment.reviewers.<your login>.interest` in
`.github/pr-lifecycle.yml`:

```yaml
jsenko:
  interest:
    "area/*": 0.4              # everything, at a low base
    "area/operator/*": 0.6     # operator and everything nested under it
    "area/CLI": 1.0            # exactly area/CLI
    "area/ui/editors": 0       # carve one label out of the base
```

- **Weights are absolute interest, 0 to 1.** They don't need to add up to anything.
  A PR's interest score is your **highest** weight among its most specific labels (a PR
  labeled `area/storage` and `area/storage/sql` counts only `area/storage/sql`). A label
  that none of your patterns match counts as 0. The highest rather than the average,
  because PRs average two to three labels and some are incidental (`area/QE` for touching
  a test): averaging diluted specialists out of their own areas.
- **Patterns:** `area/ui` matches only that label; `area/ui/*` matches `area/ui` and
  everything nested under it; `area/*` matches every area label. The most specific
  pattern wins, and an exact pattern beats `/*` for the same label, which is what lets
  a `0` switch off one area inside a broader one.
- **Your weight on an area decides whether the swap can hand you its PRs.** A runner-up
  only takes over an overloaded winner's PR if their interest in its primary area is
  above 0. A `0`, or no matching pattern, keeps the swap from giving you those PRs.
- **How much it moves the score:** interest is 30% of it. Going from 0.4 to 1.0 on a PR's
  best label adds 0.18. That's enough to decide between people with similar ownership,
  but it won't beat someone who wrote most of the changed code. That person may still be
  swapped out if they're overloaded.
- **Taking a break:** remove your entry from `reviewers` (and from
  `bot_rotation.reviewers`). You can still get picked by hand, just not by the bot.

Some examples:

| I want to... | Change |
|---|---|
| Get more PRs in an area | Raise that pattern, e.g. `"area/ui/*": 1.0` |
| Stop getting an area | Set it to `0`, or remove the pattern if you have no `area/*` base |
| Review only a few areas | Remove `area/*` and list just those areas |
| Share an area with someone more evenly | Give it the same weight as theirs. Ties on interest are decided by ownership, then by the swap |
| Get Renovate PRs, or stop getting them | Add or remove yourself in `bot_rotation.reviewers` |

The Scripts Tests workflow checks the file whenever it changes:
- every reviewer is a maintainer;
- every pattern names a real label;
- every weight is between 0 and 1;
- **every area label still has at least one interested reviewer.**

So if you are the only one covering a label, someone else has to pick it up before you
can drop it. Changes take effect once merged to `main`.

**2. The area labels.** Interest can only be as good as the labels a PR gets, and the
strongest label also picks the primary area that decides the swap. They are assigned by
the embedding classifier, from the descriptions in
`.github/scripts/label-classification/label-descriptions.yml`. If PRs in your area keep
getting the wrong labels, or none:
- fix the label by hand on the PR. The classifier never re-adds a label someone removed,
  and those corrections are what it can later be measured against. It doesn't change who
  was assigned, though, because assignment happened when the PR opened.
- improve the label's description or threshold. The
  [classifier README](scripts/label-classification/README.md) covers tuning and measuring.
- if the area needs a label of its own, add it. It has to exist on GitHub with exactly
  that name, and it must be nested where it belongs, because nesting drives both the
  classifier and the `/*` patterns above.

The other two signals aren't per-person settings. Git ownership follows the code you
commit to. Issue authorship follows the issues you open: if you want to follow a fix,
open the issue for it. The signal weights, the ownership window and the swap thresholds
are shared by everyone (`reviewer_assignment.weights`, `.ownership`, `.fairness`), so
change those in a PR the other maintainers agree with. For calibration: in the backtest
done for #9005 (334 contributor PRs), moving the weights anywhere between ownership
0.60 / interest 0.25 and 0.45 / 0.40 changed the shares by only a couple of points; the
interest maps and the swap matter much more.

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
- **stale.days_until_review_overdue** — days blocked on a maintainer before applying
  `lifecycle/review-overdue` (default: 14). Measured from when the PR became
  `lifecycle/waiting-on-maintainer`, not from its last update: bots and CI keep touching
  an unreviewed PR, so `updated_at` barely tracks review latency.
- **stale.days_until_review_ping** — days blocked on a maintainer before commenting to
  ping assignees and reviewers (default: 30). Split from the label above because the
  label is free and queryable while the comment notifies people; one ping per period of
  being blocked, never a close.
- **welcome_message** — message posted when a PR is opened
- **reviewer_assignment** — automatic reviewer assignment: signal weights, the ownership
  window, load and swap thresholds (`fairness`), the Renovate rotation pool, and each
  reviewer's interest map. See
  [Reviewer assignment](#reviewer-assignment). Every key is required (there are no
  built-in defaults to drift from the file); remove the whole section to turn it off.

## Disabling the Orchestrator

The orchestrator is enabled by default on all PRs. To exclude a specific PR, a maintainer
can add the `orchestrator/disabled` label. This reverts the PR to legacy behavior (full
test suite on every push, `DO NOT MERGE` label support).
</content>
