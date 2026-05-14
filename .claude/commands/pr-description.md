---
description: Generate or update a PR description following the Apicurio Registry PR template
---

You are helping to create a Pull Request description for the Apicurio Registry project.

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
```

## Steps

1. **Gather context**:
   - Read the git diff: `git diff main...HEAD`
   - Check for associated issues (branch name, commit messages)
   - Review commits: `git log main..HEAD --oneline`

2. **Generate the PR description** with all four sections

3. **Present to user** for review and approval

4. **Create the PR** when approved:
   ```bash
   gh pr create --title "<title>" --body "<description>"
   ```

## Guidelines

- Be specific — mention actual file names, class names, and method names
- For bug fixes, clearly explain what was broken
- For features, explain the motivation and design decisions
- Use markdown checkboxes in test plan: `- [ ]` for uncompleted, `- [x]` for completed
- Keep the title under 70 characters, conventional commit style
