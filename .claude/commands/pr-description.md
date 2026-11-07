---
description: Generate or update a PR description following the Apicurio Registry PR template
agent: repo-ops
---

You are helping to create a Pull Request description for the Apicurio Registry project.

Use `AGENT-VERIFICATION.md` to summarize existing evidence. Reuse the agreed criteria,
review level, and verification results; drafting a description does not require
restarting implementation or verification. Never manufacture missing evidence.

## Input

$ARGUMENTS — Optional context, issue, or diff scope.

## PR Description Format

The PR description must follow this structure:

```markdown
## Summary
[Brief description of what this PR does. Include "Fixes #XXXX" if it addresses an issue]

## Root Cause
[Explanation of what was causing the problem or why this change is needed]

## Changes
[Detailed bulleted list of changes:
- File/class modifications
- New files/classes added
- Specific methods or functions changed]

## Test plan
[Checklist with checkboxes:
- [ ] Specific tests added or modified
- [ ] Integration test results
- [ ] Manual testing steps if applicable
- [ ] Storage variant testing (SQL, KafkaSQL, etc.)]

Review level: [Light / Standard / Focused, with reason]

| Acceptance criterion | Executed evidence and result | Gap |
| --- | --- | --- |
| [Expected behavior] | [Exact command/test, revision, variant, result] | [None or missing evidence] |

[For fixes: expected regression failure before and success after, or an explicit gap]
[Public-interface reproduction: steps or link, executed/unexecuted]
[Specific critical decisions needing human inspection, if applicable]
```

## Steps

1. **Gather context**:
   - Establish the intended base and review the entire PR diff
   - Inspect status and remote tracking; distinguish local uncommitted work from PR content
   - Check for associated issues (branch name, commit messages)
   - Review all commits included from that base
   - Inspect available test results and CI for the matching revision; flag stale evidence

2. **Generate the PR description** with all four sections

3. **Present to user** for review and approval

4. **Create the PR** only when explicitly requested, after required submission checks:
   ```bash
   gh pr create --title "<title>" --body "<description>"
   ```

## Guidelines

- Be specific — mention actual file names, class names, and method names
- For bug fixes, clearly explain what was broken
- For features, explain the motivation and design decisions
- Use markdown checkboxes in test plan: `- [ ]` for uncompleted, `- [x]` for completed
- Check a verification box only when execution evidence exists; proposed tests and
  passing static reviews do not establish that behavior was tested
- Keep the title under 70 characters, conventional commit style
