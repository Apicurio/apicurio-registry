---
name: apicurio-test-quality
description: Score-based test quality review using the project's 34-pattern catalog (P1-P34). Detects concurrency hazards, timing flakes, CI waste, lifecycle bugs, correctness gaps, and race test quality in changed test files. Labels (concurrency, timing, waste, lifecycle, correctness, hygiene, race) enable targeted agent sweeps. Run before committing test code, and on any diff that changes concurrency primitives.
---

# Test Quality: Pattern-Based Scoring Review

Analyze changed test files against the project's 34 documented test failure patterns
(P1-P34, see `claudedocs/test-fix-pattern-catalog.md`). Each pattern detector produces
a score; violations below threshold block submission. Patterns are labeled for targeted
agent sweeps: `concurrency`, `timing`, `waste`, `lifecycle`, `correctness`, `hygiene`, `race`.

Every pattern gates at full strength unless the catalog's "Scoring scope" table gives it a
narrower treatment. That table is the authoritative one; do not keep a second copy of it
here. Appearing in it does not mean a pattern is exempt: P32 is listed because it gates
only in its narrow form, and P33 is listed because it does not gate at all.

## When to use

- Before committing any change that touches `**/src/test/**`
- Before committing any change that touches concurrency primitives, even with no test
  files in the diff. That case is what P34 detects.
- As part of the DoD (after /simplify, before /code-review)

No CI job runs this skill. It gates at review time only, so a PR that skips it reaches
`main` with nothing stopping it.

## Phase 1: Identify Test Changes

Collect both lists before deciding anything. Use `origin/main` rather than `main`: a
local `main` that has not been fetched recently resolves the range against a stale base
and pulls in files that are already upstream.

```bash
RANGE="origin/main...HEAD"   # committed work on a branch
# RANGE="HEAD"               # uncommitted working tree; note there is no "..." here

# 1. Changed test files, including ones git does not track yet
{ git diff $RANGE --name-only; git ls-files --others --exclude-standard; } \
  | grep -E 'src/test/.*\.java$' | sort -u

# 2. Production changes to concurrency primitives (the P34 signal)
git diff $RANGE -- '*.java' '*.ddl' | grep -E '^[+-]' \
  | grep -vE '^(\+\+\+|---)' \
  | grep -vE '^[+-]\s*(import |//|\*|/\*)' \
  | grep -E '(ConcurrentHashMap|AtomicReference|AtomicBoolean|AtomicInteger|volatile |synchronized |compareAndSet|containsKey|computeIfAbsent|putIfAbsent|ON CONFLICT|ON DUPLICATE KEY|MERGE INTO|FOR UPDATE)'
```

Three ways this collection silently returns nothing, all of which land on the clean-score
branch below:

- `HEAD...HEAD` is the empty diff. For uncommitted work use `RANGE="HEAD"` on its own.
- On `main` itself, `origin/main...HEAD` is empty even with a dirty tree. Check out the
  branch, or use `RANGE="HEAD"`.
- `git diff` never reports untracked files, so a brand-new test class is invisible to a
  range. The `git ls-files --others` arm above is what covers that case for list 1.

Both greps read `+` and `-` lines, because removing a `synchronized` or deleting a
double-checked-locking block is a concurrency change that a `^\+` filter cannot see. That
blindness is Pattern 31's own shape applied to this skill.

The pathspec is load-bearing. Without it the grep matches this file and the catalog, both of
which quote the pattern in prose, and a docs-only diff produces false hits. `.ddl` is in the
list because a uniqueness constraint is what makes an UPSERT possible and the schema lives in
`app/src/main/resources/io/apicurio/registry/storage/impl/sql/*.ddl`, not in `.java`. There is
no `.sql` entry: the only two tracked `.sql` files are an example init script and a Postgres
cleanup script, neither of which is a schema. The `^+++` and `^---` filters drop diff headers,
which would otherwise match on any path containing a pattern word. The last exclusion drops
import and comment lines: 88 of 981 raw hits, 9%, measured with `git grep -hE` over tracked
`*.java` at `origin/main` using the alternation above. None of them is itself a concurrency fix.

Treat the result as a signal, not a verdict. The map-method alternatives catch a
check-then-act sequence only when it is written with `containsKey` or friends. A fix that
replaces a hand-rolled `get()` test with a guarded write matches nothing, so read the
production hunks when the change description suggests a race even if this returns empty.

Pass list 2 to Agent 4 whenever it is non-empty, regardless of whether list 1 is empty.
A concurrency fix that happens to touch one unrelated test file still scores 0 on P34 if
no test exercises the race, and gating the check on an empty test list misses exactly
that case. When both lists are empty, report "No test files in diff, score: 10/10" and
stop: do not launch the agents.

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

### Agent 2: Timing and CI Waste [labels: `timing`, `waste`] (P2, P4, P9, P17, P21, P24, P27, P28, P29, P32)

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
- **P32 (QuarkusTest Overuse), narrow form only**: a NEW test class annotated `@QuarkusTest`
  that has no `@Inject` field and no CDI lookup, so it boots the full runtime to exercise
  pure logic. Score down only in that case. Do NOT score a test down for using
  `@QuarkusTest` where the surrounding test classes do; see the catalog's scoring-scope
  table for why.

### Agent 3: Infrastructure, Lifecycle, and Hygiene [labels: `lifecycle`, `hygiene`] (P8, P11, P14, P16, P19, P22, P23, P30, P33)

Detect test infrastructure mismanagement and code hygiene issues.

- **P8 (Failsafe Rerun Breakage)**: `@AfterAll` destroying deployment. Reruns start with
  dead infra.
- **P11 (KafkaConsumer Shutdown)**: Direct `consumer.close()` from wrong thread.
- **P14 (Unclosed Consumer Leak)**: KafkaConsumer in `@BeforeAll` with no `@AfterAll` close.
- **P16 (Silent Cleanup Failure)**: `catch (Exception ignored) {}` in cleanup or setup code.
  Also check `TestExecutionListener` implementations that swallow setup exceptions.
- **P19 (Reflective CDI Bypass)**: `Field.setAccessible(true)` OR `Method.setAccessible(true)`
  to inject mocks or invoke private methods. Brittle against renames.
- **P22 (Orphaned Disabled Test)**: `@Disabled` with no issue reference. Dead code.
- **P23 (Unbounded Recursive Retry)**: Recursive retry with no counter. StackOverflowError.
- **P30 (Third-Party Thread Leak)**: `Unreliables.retryUntilTrue/retryUntilSuccess` with a
  shared mutable resource (consumer, producer). The library leaks background threads that
  continue accessing the resource after timeout. Replace with caller-thread poll loop.
- **P33 (ParameterizedTest Underuse), advisory only**: several test methods differing only
  in their input values, where `@ParameterizedTest` with `@ValueSource` or `@CsvSource`
  would express the same coverage. Report these in the advisory section. Do not assign
  them a score and do not let them move this agent's number.

### Agent 4: Correctness and Race Quality [labels: `correctness`, `race`] (P3, P10, P13, P15, P18, P20, P25, P26, P31, P34)

Evaluate test correctness and race condition testing quality.

- **P3 (Non-Deterministic Race Tests)**: Threading without deterministic coordination.
  Score 10 for `CyclicBarrier`, `CountDownLatch` or Byteman rule injection; score 0 for
  "hope-based" threading. Before suggesting Byteman as the fix, read Pattern 3's
  Infrastructure note in the catalog: it records whether `-Pbyteman` exists on the current
  base. When it does not, ask for a barrier.
- **P10 (Stampede Test Completeness)**: Needs: latency, barrier, exact count assertion.
- **P13 (Assertion-Free Test)**: Zero assertions; only verifies "no exception thrown."
- **P15 (Cross-Test Event Bleed)**: Shared consumer accumulates events across tests.
- **P18 (Untyped Exception Expectation)**: `fail()+catch(ignored)` instead of `assertThrows`.
- **P20 (Silent Validation Sink)**: Validation helper catches exceptions and returns normally.
- **P25 (Benchmark as Test)**: `@Test` on benchmark methods with no assertions.
- **P26 (Existence-Only Assertion)**: Only `assertNotNull`, no value checks.
- **P31 (Vacuous Predicate)**: `allMatch()`, `noneMatch()` or a loop
  body asserted over a collection that can be empty. The assertion passes with zero
  elements. Also flag any assertion whose failure condition is unreachable, such as a
  disjunction satisfied by two opposite states (`isInterrupted() || !isAlive()`).
- **P34 (Concurrency Fix Without Race Test)**: the production side of the diff changes a
  concurrency primitive or a check-then-act sequence, and no test exercises the race.
  The Phase 1 grep is a signal, not a verdict. The alternation's own literals are common in
  single-threaded code: at `origin/main`, `git grep -lE 'containsKey' -- '*/src/main/java/*.java'`
  reports 55 files and the same command for `'synchronized '` reports 28. The trailing space
  matters, and dropping it reports 34. Read the hunk before scoring.
  Score 0 only when a deterministic test is feasible and absent. When the
  window is genuinely too narrow to force open, the pattern clears on a pointer to a test
  that covers the same path, or on a reason that names the mechanism blocking a
  deterministic test: no injection point, no barrier reachable from the test, a race
  entirely inside a third-party call. A reason that only asserts the race is hard to
  reproduce is not one of those, or the gate bypasses itself on prose. When a test is
  present, ask
  whether it fails against the unfixed code, and say so if that has not been proven by
  mutation. Ask separately whether it runs in a CI job that actually executes: a test
  behind an opt-in profile does not stop a revert.

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
| Timing & CI Waste | `timing`,`waste` | P2,P4,P9,P17,P21,P24,P27,P28,P29,P32 | X/10 | N findings |
| Infra, Lifecycle & Hygiene | `lifecycle`,`hygiene` | P8,P11,P14,P16,P19,P22,P23,P30 (+P33 advisory) | X/10 | N findings |
| Correctness & Race Quality | `correctness`,`race` | P3,P10,P13,P15,P18,P20,P25,P26,P31,P34 | X/10 | N findings |

### Findings (by severity)
[For each finding: pattern ID, file:line, description, fix suggestion]

### Advisory (not scored)
[P33 observations, if any]
```

The overall score is the weighted average:
- Concurrency Safety: weight 3 (most common failure class)
- Timing & CI Waste: weight 3
- Infra, Lifecycle & Hygiene: weight 2
- Correctness & Race Quality: weight 2

P33 is dispatched to Agent 3 but carries no score, which is why the row above lists it
separately. Agent 3's own number must not move because of it either.

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

34 patterns (P1-P34) extracted from 30+ PRs, a 661-file codebase sweep, a clean-room
validation run (August 2026), and epic #9807.

The clean-room run measured a 62.5% true-positive rate, 20 of the ~32 in-scope findings
from the earlier sweep, plus 13 files that sweep had missed. It reported no incorrect
findings, which is not the same as a measured zero false-positive rate. That run carried
P1-P26; it is what produced P27 and P28. P29-P34 came later and have no measured rate.
Known gaps: hand-rolled poll loops (P4), reflective method invocation (P19).

Labels: `concurrency`, `timing`, `waste`, `lifecycle`, `correctness`, `hygiene`, `race`.

The labels and the four agent groups are two different partitions of the catalog, so a
label sweep cuts across agents rather than selecting one. To run a targeted sweep (say,
only timing and waste patterns), pass `--labels timing,waste` and take the pattern list
from the catalog's Labels table, not from the agent headings above.
