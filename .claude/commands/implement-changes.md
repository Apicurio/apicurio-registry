---
description: Execute an approved implementation plan and produce verification evidence
agent: build
---

You are implementing changes for the Apicurio Registry project based on a plan.

Load `implement-change` (or read `.claude/skills/implement-change/SKILL.md`).
Use `AGENT-VERIFICATION.md` for review depth and evidence.

## Prerequisites

Before running this command, you should have:
- Analyzed the issue with `/analyze-gh-issue` (if applicable)
- Created a plan with `/plan-implementation`
- Received user approval on the plan

Verify you're in an Apicurio project:
```bash
git remote -v | grep -q "apicurio" || echo "Warning: Not in an Apicurio repository"
```

If no agreed criteria exist, run the framework's planning step and wait for agreement.
Reuse prior approval and the user's edge case when available.

## Input

$ARGUMENTS

If arguments specify an issue number, reference that issue in commits.

## Implementation Process

### 1. Prepare the Branch

Inspect git status and confirm the task scope. Preserve unrelated work. If a new
branch is needed, use a descriptive name such as `fix/issue-456-bug-name`.

### 2. Execute the Plan

For each task in the approved plan:

1. **Implement the change**:
   - Make the code modifications
   - Follow existing patterns and conventions
   - Adhere to project coding standards (see `.claude/rules/`)

2. **Verify the change**:
   - Map acceptance criteria to concrete assertions and relevant existing checks
   - Select supported commands/profiles from `DEVELOPING.md`
   - Use `test-compile` when tests change and run required checkstyle checks
   - For fixes, demonstrate expected regression failure before and success after,
     using an isolated workspace or other non-destructive method

3. **Commit incrementally only when explicitly requested**:
   - Otherwise leave the verified changes in the working tree
   - Stage related changes: `git add <files>`
   - Write clear commit messages following Conventional Commits format
   - Reference the issue number: `Fixes #123` or `Relates to #123`

### 3. Testing

- Write unit tests alongside new functionality
- Run tests after each logical change
- For storage-related changes, verify compatibility with different storage variants
- Run relevant specialist skills, including test-quality review when tests change
- Verify the user's edge case and provide a public-interface reproduction for behavior changes

### 4. Build Verification

After completing implementation:

Complete the checks required by the selected review level and repository instructions.
Record exact commands, results, environments/variants, and skips. Do not rerun
successful checks without a reason or treat a build with skipped tests as test evidence.
Mark unexecuted reproductions and blocked before/after checks explicitly.

### 5. Code Quality

- Remove any debug code or temporary comments
- Verify no unintended files are staged

## Commit Guidelines

- Use Conventional Commits: `<type>(<scope>): <description>`
- Each commit should be atomic and focused
- Follow repository pre-commit checks and include DCO sign-off
- Never include Claude as author — use the local git user

## Error Handling

- **Build failures**: Check error messages, fix compilation issues
- **Test failures**: Analyze failing tests, fix issues or update tests if behavior change is intentional
- **Merge conflicts**: Use `git status` to identify, resolve manually
- **Checkstyle violations**: Run checkstyle to see details, fix formatting

## Output

After implementation:
1. Return the evidence summary from `AGENT-VERIFICATION.md`
2. Include the current review level, criterion-to-evidence mapping, and verification gaps
3. Identify specific code sections and decisions requiring human inspection
4. List commits only if committing was requested and performed
5. State whether evidence is sufficient for the level or further verification/human review is needed
