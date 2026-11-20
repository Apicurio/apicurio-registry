---
description: Analyze a failing CI workflow run to diagnose the root cause
argument-hint: [run-id-or-url]
agent: ci-debugger
---

Diagnose a failing GitHub Actions workflow run for the Apicurio Registry project.

Use `AGENT-VERIFICATION.md` to report evidence and gaps. Reuse the change's criteria,
scope, and review level if available. This command diagnoses and reports; implementation
belongs in `/implement-changes` after criteria are agreed.

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
   - Checkstyle: inspect the actual rule and severity in `.checkstyle/checkstyle.xml`;
     staged warnings do not fail the build and per-file license headers are not checked

## Evidence report

- Identify the run URL, tested commit, failing job/variant, and exact failure message.
- Distinguish an observed behavioral regression from compilation, setup, infrastructure,
  and timing hypotheses; a single timeout does not prove a flaky test.
- Map the failure to affected acceptance criteria, if known. State the smallest useful
  reproduction and whether it ran, with exact commands/results.
- For a proposed fix, specify the expected failing-before/passing-after check and any
  focused human inspection needed. Do not claim the fix is verified until executed.
- Report unrelated test failures separately with the class, error, and run link, following
  repository reporting requirements. A successful retry alone is not proof of correctness.
