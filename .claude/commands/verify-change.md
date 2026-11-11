---
description: Verify an existing change or challenge its blind spots without editing code or tests
agent: build
---

Review this existing change:

$ARGUMENTS

Select the skill from the user's intent:
- By default, use `verify-change` to check behavior against acceptance criteria.
- When explicitly asked to challenge the change, investigate blind spots, or perform
  adversarial review, use `challenge-change`.
- If both are requested, verify first, then challenge using the gathered evidence.

Load the selected skill with the skill tool, or read
`.claude/skills/verify-change/SKILL.md` or `.claude/skills/challenge-change/SKILL.md`.
Follow its scope, specialist integration, and evidence-reporting instructions.
Keep implementation and tests unchanged in either case.

Reuse context from the conversation. With no arguments, verify the current change
if its scope is clear; otherwise ask what to verify.

Examples:
- `/verify-change the changes to <paths> against the agreed criteria`
- `/verify-change Challenge the current changes for blind spots`

Planning and implementation belong in `/plan-implementation` and `/implement-changes`.
If the request asks for either instead of review, direct the user there and stop.
