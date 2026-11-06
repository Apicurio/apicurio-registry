---
description: Review an existing pull request for code quality, tests, and documentation
argument-hint: [pr-number-or-url]
agent: build
---

You are reviewing a Pull Request for the Apicurio Registry project.

Load `challenge-change` (or read `.claude/skills/challenge-change/SKILL.md`).
Use `AGENT-VERIFICATION.md` for risk-based review and the
evidence summary. Keep implementation and tests unchanged throughout this review.

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

Establish behavioral criteria from the issue and user context; label inferred
criteria unconfirmed. Choose Light, Standard, or Focused review and inspect the
entire PR scope, not just the latest commit. Verify local checks target the PR
revision; if it differs from the working tree, use an isolated workspace or
report the checks as unexecuted.

Evaluate against:

- **Correctness**: Does the code do what it's supposed to do?
- **Style**: Does it follow project conventions (checkstyle, naming, imports)?
- **Tests**: Are there adequate unit and integration tests?
- **Documentation**: Are public APIs documented?
- **Security**: Any potential security issues?
- **Performance**: Any potential performance concerns?
- **Breaking changes**: Does this break backward compatibility?

Use relevant specialist skills from the shared playbook, with report-only
test-quality review. Prioritize consequential decisions over style suggestions.
Identify a plausible failure the existing checks would miss and run a targeted
check when feasible. Inspect removed/weakened tests and actual assertions.

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

Include the playbook's evidence summary: criterion-to-check mapping, exact results,
before/after evidence if available, variants, gaps, and code pointers for human
inspection. Separate observed defects from hypotheses. CI evidence must match the
reviewed revision; green checks establish only the coverage actually exercised.
Treat the verdict as a recommendation, not a substitute for required human review.

### 6. Submit Review (Optional)

Submit a GitHub review only when the user explicitly requests it.

```bash
# Approve
gh pr review <number> --approve --body "LGTM! <comments>"

# Request changes
gh pr review <number> --request-changes --body "<feedback>"

# Comment only
gh pr review <number> --comment --body "<feedback>"
```
