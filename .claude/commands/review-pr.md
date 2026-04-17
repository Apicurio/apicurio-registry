---
description: Review an existing pull request for code quality, tests, and documentation
argument-hint: [pr-number-or-url]
---

You are reviewing a Pull Request for the Apicurio Registry project.

## Input

PR: $ARGUMENTS

Accepts:
- PR number: `123`
- Full URL: `https://github.com/Apicurio/apicurio-registry/pull/123`
- Repo-qualified: `Apicurio/apicurio-registry#123`

If no PR is provided, ask the user for the PR number or URL.

## Review Process

### 1. Fetch PR Information

```bash
gh pr view <number> --json title,body,commits,files,reviews,comments,labels,author,baseRefName,headRefName
```

### 2. Analyze the Changes

1. **Understand the purpose**: Read the PR description and linked issues
2. **Review the diff**: `gh pr diff <number>`
3. **Check affected files**:
   - Identify which modules are affected
   - Look for changes to public APIs
   - Check for storage layer changes affecting multiple variants

### 3. Code Quality Assessment

Evaluate against:

- **Correctness**: Does the code do what it's supposed to do?
- **Style**: Does it follow project conventions (checkstyle, naming, imports)?
- **Tests**: Are there adequate unit and integration tests?
- **Documentation**: Are public APIs documented?
- **Security**: Any potential security issues?
- **Performance**: Any potential performance concerns?
- **Breaking changes**: Does this break backward compatibility?

### 4. Check CI Status

```bash
gh pr checks <number>
```

### 5. Provide Review Summary

- **Summary**: Brief description of what the PR does
- **Strengths**: What's done well
- **Concerns**: Issues that should be addressed
- **Suggestions**: Optional improvements
- **Verdict**: Approve / Request Changes / Comment

### 6. Submit Review (Optional)

```bash
# Approve
gh pr review <number> --approve --body "LGTM! <comments>"

# Request changes
gh pr review <number> --request-changes --body "<feedback>"

# Comment only
gh pr review <number> --comment --body "<feedback>"
```
