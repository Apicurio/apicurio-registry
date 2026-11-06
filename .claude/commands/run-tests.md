---
description: Run relevant checks and map observed results to behavioral acceptance criteria
argument-hint: [module-name-or-test-class]
agent: repo-ops
---

Run tests for Apicurio Registry using `AGENT-VERIFICATION.md` as the evidence framework.

Load `verify-change` (or read `.claude/skills/verify-change/SKILL.md`) for its scope,
report-only boundaries, and evidence requirements. This command performs the requested
test-execution portion of verification, not a full behavioral review; report remaining
evidence gaps without expanding beyond the requested test scope.

## Input

$ARGUMENTS — Optional: module name, test class, or "all"

## Steps

1. **Establish scope:** Honor explicit arguments; otherwise inspect git status and
   the relevant diff, including task-related staged, unstaged, and untracked files.
   Reuse agreed criteria and review level. If scope is ambiguous, ask before running
   broad suites. Do not include unrelated local changes by default.
2. **Select supported checks:** Read `DEVELOPING.md` and relevant module scripts.
   Map criteria to existing tests. Use actual project profiles and npm scripts rather
   than assuming commands exist. Include required compilation and checkstyle checks;
   use `test-compile` when tests change.
3. **Cover affected variants:** For shared storage changes, establish coverage across
   SQL, KafkaSQL, GitOps, and KubernetesOps. Run supported relevant checks when feasible
   and report infrastructure blockers or missing variant coverage explicitly.
4. **Gather regression evidence:** For bug fixes with a regression test, establish
   expected failure before and success after in an isolated workspace or another
   non-destructive setup. Setup/build failures do not count as behavioral failures.
   If blocked, record the gap.
5. **Assess changed tests:** Run `apicurio-test-quality` when required, in report-only
   mode. Its score is separate from test execution and behavioral coverage.
6. **Report:** Use the playbook's evidence summary, listing exact commands, revision,
   environment/variant, pass/fail/skip results, failing test names, and coverage gaps.
   Mark absent criteria unconfirmed and distinguish executed checks from suggestions.

Keep implementation and tests unchanged. Stop after relevant checks complete unless
failures or unresolved concerns justify more testing. Do not claim full verification
from a subset of checks or silently weaken tests to obtain a pass.
