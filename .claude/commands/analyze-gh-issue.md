---
description: Start work on a GitHub issue
argument-hint: [issue-number-or-url]
---

You are starting work on a GitHub issue to understand its requirements and identify affected code.

## Input

Issue: $ARGUMENTS

Accepts:
- Issue number: `123`
- Full URL: `https://github.com/Apicurio/apicurio-registry/issues/123`
- Repo-qualified: `Apicurio/apicurio-registry#123`

If no issue is provided, ask the user for the issue number or URL.

## Steps

1. **Fetch Issue Details**: Retrieve the issue title, description, labels, and comments from GitHub:
   ```bash
   gh issue view $ARGUMENTS --json title,body,labels,comments,assignees
   ```

2. **Summarize**: Present the issue title, description, acceptance criteria, and labels.

3. **Identify Affected Code**: Based on the issue description, search the codebase for relevant files, classes, and modules.

4. **Create a worktree** for the issue starting from the main branch:
   ```bash
   git worktree add -b issue-<number> ../issue-<number> main
   ```

5. **Wait for user confirmation** before proceeding with implementation.
