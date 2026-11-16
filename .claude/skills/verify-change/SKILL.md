---
name: verify-change
description: Verify an existing change against behavioral criteria using tests, reproductions, and explicit evidence gaps. Use when the user asks to check an implementation or invokes /verify-change; report defects without editing implementation or tests.
---

# Verify a Change

Read `AGENT-VERIFICATION.md` at the repository root. Follow its shared workflow
contract and section 3's evidence requirements. This is a report-only pass: leave
implementation and tests unchanged, including during specialist reviews.

1. Establish the requested diff and revision, including relevant uncommitted files.
   Reuse agreed criteria and review level. If criteria are absent, infer a short
   provisional list, label it unconfirmed, and ask the user to confirm intent while
   continuing independent checks. Ask before proceeding if the scope itself is unclear.
2. Map each criterion to actual assertions, existing checks, and relevant specialist
   evidence. Inspect test content rather than trusting test names or prior summaries.
3. Execute relevant supported checks where feasible. Verify the user's edge case,
   affected variants, and concrete resulting state as applicable. For fixes, gather
   expected failing-before/passing-after evidence using a non-destructive setup.
4. Run or provide a minimal public-interface reproduction for behavior changes,
   marking execution status. Report absent regression tests or coverage as gaps;
   do not write tests or fix the implementation during this pass.

Return the shared evidence summary, actionable defects, and pointers to decisions
requiring human inspection. Separate executed evidence from proposed checks and
static test-quality scores. Stop after reporting; fixes belong in `implement-change`
with agreed criteria. A deeper blind-spot investigation belongs in `challenge-change`.
