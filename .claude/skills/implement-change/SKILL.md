---
name: implement-change
description: Implement approved behavioral criteria and produce executed verification evidence. Use when the user asks to execute an agreed plan or invokes /implement-changes.
---

# Implement a Change

Read `AGENT-VERIFICATION.md` at the repository root. Follow its shared workflow
contract and section 3. This skill may edit the scoped implementation and tests.

1. Reuse approved criteria, plan, review level, and the user's edge case. If criteria
   are missing, load `plan-change` (or read its skill file) and stop for agreement.
2. Inspect status and preserve unrelated work. Implement the smallest change that
   satisfies the agreed criteria, following repository conventions and relevant
   specialist skills from the shared contract.
3. Check concrete outcomes, including resulting state and failure cases where
   relevant. Use existing tests as a baseline and add meaningful coverage as required.
4. Execute relevant supported checks from `DEVELOPING.md`. For a fix, demonstrate
   expected regression failure before and success after without disturbing the user's
   working tree. Report blockers rather than substituting setup failures for evidence.
5. Provide a public-interface reproduction for behavior changes, with setup, expected
   results, and cleanup. Mark it executed or unexecuted. Run required test-quality
   review when tests change. Reassess review depth if the scope grows.

Return the shared evidence summary and remaining human decision. Do not rerun
successful checks without a reason. Do not commit or push unless explicitly requested.
If further analysis is warranted, recommend `/verify-change`, explicitly requesting
a challenge when blind-spot investigation is needed;
do not silently broaden the implementation beyond the agreed task.
