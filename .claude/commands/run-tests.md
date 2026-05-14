---
description: Smart test runner that identifies affected modules and runs appropriate test suites
argument-hint: [module-name-or-test-class]
---

Smart test runner for the Apicurio Registry project.

## Input

$ARGUMENTS — Optional: module name, test class, or "all"

## Steps

1. **Detect affected modules** (if no arguments provided):
   ```bash
   git diff --name-only main
   ```
   Map changed files to their Maven modules.

2. **Run unit tests** for each affected module:
   ```bash
   ./mvnw test -pl <module>
   ```

3. **Run checkstyle** for each affected module:
   ```bash
   ./mvnw checkstyle:check -pl <module>
   ```

4. **If storage layer was changed**, suggest integration tests:
   ```bash
   ./mvnw verify -pl integration-tests -Plocal-tests
   ```

5. **If UI was changed**:
   ```bash
   cd ui/ui-app && npm test
   ```

6. **Report results**: pass/fail per module, failing test names, and suggested fixes.
