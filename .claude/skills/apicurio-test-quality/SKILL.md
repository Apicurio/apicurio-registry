---
name: apicurio-test-quality
description: Score-based test quality review using the project's 30-pattern catalog (P1-P30). Detects concurrency hazards, timing flakes, CI waste, lifecycle bugs, correctness gaps, and race test quality in changed test files. Labels (concurrency, timing, waste, lifecycle, correctness, hygiene, race) enable targeted agent sweeps. Run before committing test code.
---

# Test Quality: Pattern-Based Scoring Review

Analyze changed test files against the project's 30 documented test failure patterns
(P1-P30, see `claudedocs/test-fix-pattern-catalog.md`). Each pattern detector produces
a score; violations below threshold block submission. Patterns are labeled for targeted
agent sweeps: `concurrency`, `timing`, `waste`, `lifecycle`, `correctness`, `hygiene`, `race`.

## When to use

- Before committing any change that touches `**/src/test/**`
- As part of the DoD (after /simplify, before /code-review)
- On CI as a PR quality gate

## Phase 1: Identify Test Changes

```bash
# Changed test files in the current diff
git diff --name-only HEAD | grep -E 'src/test/.*\.java$'
# Or vs main for PR scope
git diff main...HEAD --name-only | grep -E 'src/test/.*\.java$'
```

If no test files are changed, report "No test files in diff, score: 10/10" and stop.

Read the full content of each changed test file (not just the diff). Also read the
production code they test (to understand shared state, lifecycle, config properties).
Read the pattern catalog from `claudedocs/test-fix-pattern-catalog.md`.

**IMPORTANT**: Collect all file contents in Phase 1 BEFORE launching agents. The detector
agents may not have shell access (e.g., `code-reviewer` has only Read/Grep/Glob). You
must pass the file contents directly in each agent's prompt. Never instruct an agent to
run `git show` or `git diff`; instead, inline the content you already read.

## Phase 2: Launch Pattern Detector Agents

Use the Agent tool to launch **four** detector agents concurrently. Pass each agent
the **full file contents** (inlined in the prompt) AND the relevant pattern descriptions.
Do NOT use `subagent_type: code-reviewer` (it lacks Bash). Use default agents or
`general-purpose`.

Each agent scores every changed test file on a 0-10 scale for its pattern group.
- **10**: no violations detected
- **7-9**: minor concerns (advisory)
- **4-6**: violations found, should fix
- **0-3**: critical violations, must fix before merge

Each finding must include: file, line, pattern ID, severity, and a concrete fix suggestion.

### Agent 1: Concurrency Safety [labels: `concurrency`] (P1, P5, P6, P7, P12)

Detect test code that will break under JUnit 5 class-level parallel execution.

- **P1 (Static Field Contamination)**: Mutable static fields in test classes that extend
  a shared base class. Under `@TestInstance(PER_CLASS)` with concurrent classes, static
  fields collide. Score 0 if mutable static state AND parallel classes are enabled.
- **P5 (Shared Infra Lifecycle)**: `@AfterAll`/`@AfterEach` tearing down shared
  infrastructure without reference-counting.
- **P6 (Registration Collision)**: Hardcoded resource names (connectors, topics, rules)
  without per-class uniqueness or serialization.
- **P7 (Count Assertion with Noise)**: `assertEquals(N, size())` without filtering
  by test-specific content.
- **P12 (Shared Mutable DTO)**: `private static final` DTOs mutated via setters across
  tests. `final` prevents reassignment but not mutation.

### Agent 2: Timing and CI Waste [labels: `timing`, `waste`] (P2, P4, P9, P17, P21, P24, P27, P28)

Detect test code that will flake under CI load or waste CI time.

- **P2 (TOCTOU Assertions)**: Poll-then-assert as separate operations. Fix: wrap in
  `await().untilAsserted()`.
- **P4 (Unbounded Wait)**: `await()` without `atMost()`, `Thread.sleep()` without retry,
  `future.get()` without timeout, `latch.await()` without timeout, `CompletableFuture.get()`
  without timeout. ALSO detect hand-rolled poll loops (`while + Thread.sleep` without
  a deadline or max-iteration guard).
- **P9 (Eventually-Consistent Config)**: `System.setProperty` on `@Dynamic` config
  followed by immediate assertion without retry.
- **P17 (Fixed-Sleep Flush Wait)**: `Thread.sleep(N)` for eventually-consistent operations
  instead of Awaitility. Bounded but wasteful.
- **P21 (Redundant Pre-Wait)**: `Thread.sleep()` immediately before a retry loop that
  already handles the wait. Pure CI time waste.
- **P24 (Sleep-Then-Assert-Stable)**: `Thread.sleep(30-60s)` for negative assertions
  ("nothing changed"). Fix: `await().during().atMost().untilAsserted()`.
- **P27 (Side Effects in Awaitility Lambda)**: Mutating operations (create, insert, register)
  inside `await().untilAsserted()` execute on every retry. Separate mutation from polling.
- **P28 (Awaitility Error Swallowing)**: `await().until(() -> expr)` swallows exceptions
  and retries. Use `untilAsserted()` when the lambda can throw unexpected errors.
- **P29 (Async-Treated-as-Sync)**: An async operation (K8s delete, container stop) treated
  as complete because the API returned 200. Must poll for actual completion. Look for
  `delete()` or `stop()` calls on K8s/Docker resources followed by no wait/poll.

### Agent 3: Infrastructure, Lifecycle, and Hygiene [labels: `lifecycle`, `hygiene`] (P8, P11, P14, P16, P19, P22, P23)

Detect test infrastructure mismanagement and code hygiene issues.

- **P8 (Failsafe Rerun Breakage)**: `@AfterAll` destroying deployment. Reruns start with
  dead infra.
- **P11 (KafkaConsumer Shutdown)**: Direct `consumer.close()` from wrong thread.
- **P14 (Unclosed Consumer Leak)**: KafkaConsumer in `@BeforeAll` with no `@AfterAll` close.
- **P16 (Silent Cleanup Failure)**: `catch (Exception ignored) {}` in cleanup or setup code.
  Also check `TestExecutionListener` implementations that swallow setup exceptions.
- **P30 (Third-Party Thread Leak)**: `Unreliables.retryUntilTrue/retryUntilSuccess` with a
  shared mutable resource (consumer, producer). The library leaks background threads that
  continue accessing the resource after timeout. Replace with caller-thread poll loop.
- **P19 (Reflective CDI Bypass)**: `Field.setAccessible(true)` OR `Method.setAccessible(true)`
  to inject mocks or invoke private methods. Brittle against renames.
- **P22 (Orphaned Disabled Test)**: `@Disabled` with no issue reference. Dead code.
- **P23 (Unbounded Recursive Retry)**: Recursive retry with no counter. StackOverflowError.

### Agent 4: Correctness and Race Quality [labels: `correctness`, `race`] (P3, P10, P13, P15, P18, P20, P25, P26)

Evaluate test correctness and race condition testing quality.

- **P3 (Non-Deterministic Race Tests)**: Threading without deterministic coordination.
  Score 10 for Byteman/CyclicBarrier; score 0 for "hope-based" threading.
- **P10 (Stampede Test Completeness)**: Needs: latency, barrier, exact count assertion.
- **P13 (Assertion-Free Test)**: Zero assertions; only verifies "no exception thrown."
- **P15 (Cross-Test Event Bleed)**: Shared consumer accumulates events across tests.
- **P18 (Untyped Exception Expectation)**: `fail()+catch(ignored)` instead of `assertThrows`.
- **P20 (Silent Validation Sink)**: Validation helper catches exceptions and returns normally.
- **P25 (Benchmark as Test)**: `@Test` on benchmark methods with no assertions.
- **P26 (Existence-Only Assertion)**: Only `assertNotNull`, no value checks.

## Phase 3: Score and Report

Wait for all four agents. Aggregate findings into a scored report:

```
## Test Quality Report

**Overall Score**: X.X / 10.0
**Verdict**: PASS (>= 7.0) | WARN (5.0-6.9) | FAIL (< 5.0)

### Scores by Category
| Category | Labels | Patterns | Score | Findings |
|----------|--------|----------|-------|----------|
| Concurrency Safety | `concurrency` | P1,P5,P6,P7,P12 | X/10 | N findings |
| Timing & CI Waste | `timing`,`waste` | P2,P4,P9,P17,P21,P24,P27,P28,P29 | X/10 | N findings |
| Infra, Lifecycle & Hygiene | `lifecycle`,`hygiene` | P8,P11,P14,P16,P19,P22,P23,P30 | X/10 | N findings |
| Correctness & Race Quality | `correctness`,`race` | P3,P10,P13,P15,P18,P20,P25,P26 | X/10 | N findings |

### Findings (by severity)
[For each finding: pattern ID, file:line, description, fix suggestion]
```

The overall score is the weighted average:
- Concurrency Safety: weight 3 (most common failure class)
- Timing & Consistency: weight 3
- Infra & Lifecycle: weight 2
- Race Test Quality: weight 2

## Phase 4: Fix (if score < 7.0)

For each finding scored 6 or below, apply the fix directly. Use the pattern catalog
(`claudedocs/test-fix-pattern-catalog.md`) for the fix template.

After fixing, re-run the affected detectors to verify the score improved.

## Scoring Calibration

The scores are calibrated against the project's actual history:
- **Score 3 or below**: the exact pattern that caused PR #9798, #9757, or #9722 failures
- **Score 5-6**: a pattern that caused flaky tests (retried but not deterministic)
- **Score 7-8**: minor concern, unlikely to cause CI failure but not best practice
- **Score 9-10**: clean, follows all documented patterns

## Pattern Reference

Full catalog with examples, mechanisms, fix templates, and labels:
`claudedocs/test-fix-pattern-catalog.md`

30 patterns (P1-P30) extracted from 30+ PRs, a 661-file codebase sweep, and a clean-room
validation run (August 2026). Clean-room validation: 62.5% true-positive rate, zero false
positives. Known gaps: hand-rolled poll loops (P4), reflective method invocation (P19).

Labels: `concurrency`, `timing`, `waste`, `lifecycle`, `correctness`, `hygiene`, `race`.

To run a targeted sweep by label (e.g., only timing/waste patterns):
pass `--labels timing,waste` to focus the agents on those pattern groups.
