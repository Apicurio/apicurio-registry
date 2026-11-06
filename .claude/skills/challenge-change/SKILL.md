---
name: challenge-change
description: Challenge an existing change by investigating plausible blind spots despite passing checks. Use for adversarial review, /review-pr, or an explicit challenge request through /verify-change; keep implementation and tests unchanged.
---

# Challenge a Change

Read `AGENT-VERIFICATION.md` at the repository root. Follow its shared workflow
contract and section 4. This is a report-only pass: leave implementation and tests
unchanged, including during specialist reviews.

1. Establish the complete requested diff and revision, criteria, and review level.
   Label inferred intent unconfirmed. Reuse existing evidence only if it matches
   the reviewed revision and environment; an implementation summary is not proof.
2. Inspect the diff and actual assertions. Identify a plausible consequential failure
   that the current checks would miss. Look for weakened/deleted tests, shared
   implementation/test assumptions, unintended contracts/defaults, and failure paths.
3. Use relevant specialist skills in report-only mode. Investigate the strongest
   concern with a targeted existing check or reproducible experiment where feasible.
   Use isolated experiments and disposable data; do not edit project code or tests.
4. Distinguish observed defects, hypotheses, and unverified behavior. Prioritize
   actionable findings over speculative lists or style suggestions. Avoid repeating
   a full verification suite unless new evidence or unresolved concerns justify it.

Return the shared evidence summary, findings with file:line or method references,
and exact decisions requiring human inspection. If no defect is found, state the
limits of the investigation. Stop after reporting; do not fix findings or submit a
GitHub review unless separately requested. Agent agreement is not human approval.
