---
description: Analyze a failing CI workflow run to diagnose the root cause
argument-hint: [run-id-or-url]
---

Diagnose a failing GitHub Actions workflow run for the Apicurio Registry project.

## Input

$ARGUMENTS — GitHub Actions run ID or URL

## Steps

1. **Fetch workflow run details**:
   ```bash
   gh run view $ARGUMENTS
   ```

2. **Identify failed jobs and steps**:
   ```bash
   gh run view $ARGUMENTS --json jobs --jq '.jobs[] | select(.conclusion == "failure") | {name, conclusion}'
   ```

3. **Fetch logs for failed jobs**:
   ```bash
   gh run view $ARGUMENTS --log-failed
   ```

4. **Categorize the failure**:
   - **Compile error**: Identify file, line, and error message
   - **Test failure**: Identify test class, method, and assertion
   - **Checkstyle violation**: Identify rule and file location
   - **Infrastructure**: Timeout, OOM, container startup failure, flaky test

5. **Search the codebase** for the failing code and suggest a fix.

6. **Common issues**:
   - Protobuf compilation: generated sources in `target/` may be stale — rebuild
   - KafkaSQL tests: timing-sensitive — may need `Awaitility` wrappers
   - UI tests: npm/node version mismatches
   - Checkstyle: locale-sensitive methods, star imports, missing license headers
