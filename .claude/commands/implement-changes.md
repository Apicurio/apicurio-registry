---
description: Execute an implementation plan with incremental commits
---

You are implementing changes for the Apicurio Registry project based on a plan.

## Prerequisites

Before running this command, you should have:
- Analyzed the issue with `/analyze-gh-issue` (if applicable)
- Created a plan with `/plan-implementation`
- Received user approval on the plan

Verify you're in an Apicurio project:
```bash
git remote -v | grep -q "apicurio" || echo "Warning: Not in an Apicurio repository"
```

If no plan exists, ask the user if they want to create one first.

## Input

$ARGUMENTS

If arguments specify an issue number, reference that issue in commits.

## Implementation Process

### 1. Prepare the Branch

```bash
git checkout -b <branch-name>
```

Use a descriptive branch name like `feature/issue-123-description` or `fix/issue-456-bug-name`.

### 2. Execute the Plan

For each task in the approved plan:

1. **Implement the change**:
   - Make the code modifications
   - Follow existing patterns and conventions
   - Adhere to project coding standards (see `.claude/rules/`)

2. **Verify the change**:
   - Ensure the code compiles: `./mvnw compile -pl <module>`
   - Run relevant unit tests: `./mvnw test -pl <module>`
   - Run checkstyle: `./mvnw checkstyle:check -pl <module>`

3. **Commit incrementally**:
   - Stage related changes: `git add <files>`
   - Write clear commit messages following Conventional Commits format
   - Reference the issue number: `Fixes #123` or `Relates to #123`

### 3. Testing

- Write unit tests alongside new functionality
- Run tests after each logical change
- For storage-related changes, verify compatibility with different storage variants

### 4. Build Verification

After completing implementation:

```bash
./mvnw clean install -DskipTests       # Full build
./mvnw test -pl <affected-modules>      # Tests for affected modules
./mvnw checkstyle:check -pl <module>    # Checkstyle verification
```

### 5. Code Quality

- Remove any debug code or temporary comments
- Verify no unintended files are staged

## Commit Guidelines

- Use Conventional Commits: `<type>(<scope>): <description>`
- Each commit should be atomic and focused
- Never include Claude as author — use the local git user

## Error Handling

- **Build failures**: Check error messages, fix compilation issues
- **Test failures**: Analyze failing tests, fix issues or update tests if behavior change is intentional
- **Merge conflicts**: Use `git status` to identify, resolve manually
- **Checkstyle violations**: Run checkstyle to see details, fix formatting

## Output

After implementation:
1. Summarize what was implemented
2. List all commits created
3. Report any test failures or issues encountered
4. Ask if user wants to run `/pr-description` to prepare the pull request
