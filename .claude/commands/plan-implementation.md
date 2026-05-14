---
description: Create a detailed implementation plan for a feature or fix
---

You are creating an implementation plan for the Apicurio Registry project.

## Input

Context: $ARGUMENTS

If no context is provided, ask the user what they want to plan. This could be:
- A GitHub issue number (run `/analyze-gh-issue` first if not already done)
- A feature description
- A bug to fix

## Prerequisites

Verify you're in an Apicurio project:
```bash
git remote -v | grep -q "apicurio" || echo "Warning: Not in an Apicurio repository"
```

## Planning Process

Enter plan mode to thoroughly explore the codebase and design the implementation.

### 1. Understand the Scope

- Review the requirements (from issue analysis or user input)
- Identify all components that need changes
- Determine if this affects the API, storage layer, UI, or multiple areas

### 2. Research Existing Patterns

- Find similar implementations in the codebase to follow as examples
- Identify coding patterns and conventions used
- Check how similar features handle testing, configuration, and documentation

### 3. Design the Solution

Create a clear plan covering:

- **Branch Strategy**: Suggest a branch name based on issue/feature
- **Files to Modify**: List existing files that need changes with specific modifications
- **New Files to Create**: Any new classes, interfaces, or resources needed
- **API Changes**: If REST API is affected, describe endpoint changes
- **Storage Changes**: If storage layer is affected, describe schema/query changes
- **Configuration**: Any new configuration properties needed
- **Testing Strategy**:
  - Unit tests to add or modify
  - Integration test requirements
  - Which storage variants need testing (SQL, KafkaSQL, GitOps, In-Memory)

### 4. Break Down into Tasks

Create a numbered list of implementation steps in logical order:
1. Each step should be independently committable
2. Steps should build on each other
3. Include test creation alongside implementation
4. End with documentation updates if needed

### 5. Identify Risks

- Note any potential breaking changes
- Flag areas that need extra review
- Identify dependencies on external systems or PRs

## Output

Present the plan in a structured format and use the ExitPlanMode tool when ready for user approval.

After plan approval, the user can run `/implement-changes` to execute the plan.
