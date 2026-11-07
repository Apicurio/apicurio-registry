---
name: plan-change
description: Plan an agent-assisted change with behavioral acceptance criteria, review depth, and a verification plan. Use before implementation when the user requests planning or invokes /plan-implementation.
---

# Plan a Change

Read `AGENT-VERIFICATION.md` at the repository root. Follow its shared workflow
contract and sections 1–2. This skill plans only; leave implementation and tests unchanged.

1. Establish the task from the request and conversation. Ask if the goal is unclear.
2. Inspect relevant code, repository instructions, and existing tests. Identify
   applicable specialist guidance using the shared contract; use it for planning,
   not for regeneration, automatic fixes, or full test-quality detector sweeps.
3. Reuse agreed criteria. Propose at most five observable input/outcome criteria,
   preserved behavior, assumptions, and unresolved questions.
4. Choose the review level by consequence of failure. Map each criterion to
   meaningful checks, including existing coverage and affected variants/contracts.
5. For fixes, plan expected failing-before/passing-after regression evidence. For
   behavior changes, plan a public-interface reproduction. Identify critical
   decisions needing human inspection and likely verification blockers.

Return the concise plan and ask the user to confirm intent and provide an independent
edge case. Stop before implementation. Do not require another approval when the
same criteria and plan have already been approved. The next step is
`/implement-changes`, not an automatic transition to editing.
