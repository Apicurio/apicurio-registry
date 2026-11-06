# Agent Verification Playbook

A lightweight framework for reviewing your own agent-assisted changes to Apicurio Registry.
The goal is to spend review effort on intent, evidence, and consequential decisions.

## Invoke from chat

Use the existing workflow commands; each loads the appropriate internal skill:

```text
/plan-implementation Fix <problem>
/implement-changes the agreed plan; also check <my edge case>
/verify-change the changes to <paths> against <criteria>
/pr-description
```

For additional scrutiny, ask explicitly:

```text
/verify-change Challenge the current changes for blind spots
```

With no arguments, `/verify-change` verifies the current change when the scope is
clear. Planning, verification, and challenge leave implementation and tests unchanged.
The workflow incorporates the existing SDK, security, storage, and test-quality
skills when relevant. You can also ask: "Use the verify-change skill for this change."
The four workflow skills (`plan-change`, `implement-change`, `verify-change`, and
`challenge-change`) stay separate internally. They do not each need a command.
`/verify-change` selects verification by default and challenge when explicitly requested.
`/review-pr <number>` applies challenge review to a GitHub PR.

After installing or changing these definitions, quit and restart OpenCode so it
loads the commands and skills. Definitions live in `.claude/`, with matching
symlinks under `.opencode/`.

The existing commands use the same framework:

| Command | Framework contribution |
| --- | --- |
| `/analyze-gh-issue`, `/plan-implementation` | `plan-change`: criteria, review level, and evidence plan |
| `/implement-changes` | `implement-change`: approved implementation and evidence |
| `/verify-change` | `verify-change` by default; `challenge-change` for explicit blind-spot review |
| `/run-tests` | Test-execution portion of `verify-change`, with coverage gaps |
| `/review-pr` | `challenge-change`: report-only review and human-review pointers |
| `/debug-ci-failure` | Observed failures, reproduction evidence, and remaining hypotheses |
| `/pr-description` | Summarize verified evidence and gaps for reviewers |

Carry criteria and results between commands in the conversation; approval and checks
do not need repeating unless the scope changes or the evidence becomes stale.

## Shared workflow contract

All four workflow skills read this document; specialist skills extend the selected
stage rather than starting another workflow.

- **Scope:** Inspect status and distinguish the requested change from unrelated
  working-tree edits. Include relevant staged, unstaged, and untracked files. For
  branch/PR reviews, establish the base and inspect the entire requested diff.
- **Intent:** Reuse agreed criteria and approval. When reviewing existing code without
  agreed criteria, label inferred criteria unconfirmed, ask the user to confirm intent,
  and continue independent checks. Ask if the task or diff scope itself is unclear.
- **Evidence:** Associate results with the actual revision and environment tested.
  Reuse current evidence; disclose stale, skipped, blocked, and unexecuted checks.
  Preserve the working tree during before/after experiments and use disposable data.
  Compilation or setup failures are not regression evidence.
- **Boundaries:** Planning stops before implementation. Verification and challenge
  report defects without editing implementation or tests, including through specialist
  skills. These are workflow instructions, not a permission sandbox; command-agent
  and tool permissions remain separate. Implementation requires agreed criteria.
- **Handoff:** Carry the task/scope, criteria and approval status, review level,
  user-selected edge case, and current evidence/gaps into the next stage. Do not
  automatically transition from review into fixing findings.
- **Output:** Use the evidence summary below for implementation, verification, and
  challenge. End with evidence sufficient for the selected level, gaps to resolve,
  or focused human review required. Agent agreement and test-quality scores are not
  proof of correctness or human approval.

### Specialist skills

Load only skills relevant to the actual change. Use the skill tool when available;
otherwise read `.claude/skills/<name>/SKILL.md`.

| Change | Skill | Contribution |
| --- | --- | --- |
| REST endpoints, models, OpenAPI | `sdk-update` | Contract and SDK compatibility evidence |
| Auth, OIDC, secrets, TLS | `security-review` | Permission/configuration boundaries and negative cases |
| Storage functionality | `storage-variant-guide` | Variant coverage, persisted state, failure behavior |
| Changed tests under `**/src/test/**` | `apicurio-test-quality` | Required test correctness and reliability review |

Pass the workflow stage, scope, criteria, review level, and edit boundary into
specialist reviews. In planning, use specialist guidance to design checks; defer
execution and full test-quality detector sweeps. In verification and challenge,
request report-only test-quality review and propagate that constraint to detectors.
Specialists must not recursively invoke workflow skills. If tools or permissions
prevent a check, report the gap.

## 1. Choose the review depth

Classify by the consequence of a defect, not diff size or the agent's confidence.
Use the highest applicable level; reassess if the scope grows.

| Level | Typical changes | Evidence and human review |
| --- | --- | --- |
| **Light** | Docs, mechanical edits, isolated and easily reversible changes | Relevant checks; skim the diff for scope and accidental changes. |
| **Standard** | Ordinary bug fixes and features | Approved behavioral criteria; meaningful tests; relevant CI; a reproducible behavior check; inspect critical decisions. For fixes, demonstrate regression failure before and success after. |
| **Focused** | Auth, storage semantics, migrations, public API compatibility, concurrency | Standard evidence plus human inspection of the affected invariants and critical code paths. Cover the relevant variants and failure cases. |

Reverting code is not sufficient recovery if a defect corrupts data or breaks deployed clients.
Existing repository requirements still apply at every level.

## 2. Agree on the outcome

Use this prompt at the start. Read the response, correct assumptions, and choose one edge case yourself.

```text
Task: <describe the problem or feature>

Before implementing, inspect the relevant code and repository instructions, then give me:
1. The intended observable behavior, in at most five acceptance criteria.
2. Existing behavior that must be preserved.
3. Assumptions or unresolved questions that could change the solution.
4. A proposed review level: Light, Standard, or Focused, with a short reason.
5. The checks that will establish each criterion, including relevant existing tests.

Express criteria as inputs and specific expected outcomes, not implementation steps.
Identify affected storage variants, API/SDK contracts, config defaults, and permission
boundaries where relevant. Verify these against the repository rather than guessing.
Keep the plan proportional to the task. Wait for my agreement before implementing.
```

## 3. Implement and produce evidence

```text
Proceed with the agreed criteria: <paste or reference them>.
Also verify this edge case: <your independently chosen scenario and expected result>.

Keep the change scoped and follow repository instructions. Preserve unrelated work.
Run the relevant checks and report what actually ran, with exact commands and results.
Use existing tests as an independent baseline and add meaningful coverage as required.
Assertions should check concrete outcomes, including resulting state where applicable.

For a bug fix, demonstrate that the regression test fails on the original implementation
for the expected behavioral reason, then passes with the fix. Use an isolated workspace
or another non-destructive method; do not reset or overwrite my working tree.
Compilation, setup, and infrastructure failures do not count as regression evidence.
If before/after verification is blocked, state the gap explicitly.

For a behavior change, provide a minimal reproducible check through the appropriate
public interface (REST API, SDK, UI, or CLI), including setup, expected results, and
cleanup. Use disposable test data. Mark it as executed or unexecuted.

Finish with the evidence summary below. Do not equate a green subset of checks with
full verification, or claim a CI result you have not inspected. Do not commit or push.
```

### Evidence summary format

```text
Behavior changed: <one or two sentences>
Review level: <level and reason; note any increase from the plan>

Criterion | Evidence (test/check and exact result) | Gaps
...

Verification commands: <commands, environment/variant, results, skipped checks>
Before/after regression: <expected failure and passing result, blocked, or not applicable>
Reproduction: <commands/steps, expected outcomes, executed or unexecuted>
Human inspection: <specific file:line or method, decision to check, and why>
```

## 4. Challenge the result

Use this when the change warrants a second pass. This can run in the same session or
a fresh one; another agent's agreement is supporting feedback, not proof.

```text
Verify this change against these acceptance criteria: <criteria>.
Review scope: <base commit and changed paths, including relevant uncommitted changes>.

Do not modify the implementation during this pass. Inspect the diff and actual test
assertions rather than relying on the implementation summary.

Identify the most plausible way the change could be wrong despite passing its checks.
Look for missing cases, weakened/deleted tests, and unintended contract or default changes.
Run a targeted existing check or reproducible experiment for that concern if feasible.
Distinguish observed defects from hypotheses and unverified behavior.

Return only actionable findings with evidence, verification gaps, and the exact code
sections needing human judgment. If you find no defects, still state the checks' limits.
```

## 5. Make the decision

Before accepting the change, answer:

- **Intent:** Do the criteria describe what I actually wanted?
- **Evidence:** Would the checks catch a plausible wrong implementation? Did my edge case pass?
- **Scope:** Does the changed-file list make sense? Skim the diff for unrelated edits and weakened tests.
- **Coverage:** Did the relevant checks and variants run? Are remaining gaps acceptable for this change?
- **Judgment:** Have I inspected the critical decisions required by the review level?

If an answer is unclear, request the missing evidence or inspect the relevant code.
For Focused changes, passing behavioral tests does not replace targeted human review.

### Registry-specific reminders

Apply only what the change touches:

- **Storage:** Evidence across all four variants for shared storage changes, including persisted state and failure behavior.
- **REST/SDK:** Contract compatibility and SDK consistency; check error responses as well as successful responses.
- **Auth:** Authorized and forbidden access, including whether a rejected operation leaves state unchanged.
- **Configuration:** Defaults agree across declarations, activation logic, and documentation.
- **Concurrency:** Tests exercise the relevant ordering or invariant; repeated green runs alone are weak evidence.
- **Checks:** Use `DEVELOPING.md` for supported build/test commands and `AGENTS.md` for contribution requirements, including test-quality review when tests change.

**Working rule:** use light review when failures are cheap and observable; inspect the
critical code when failures are consequential or difficult to detect externally.
