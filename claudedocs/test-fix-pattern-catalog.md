# Test Fix Pattern Catalog: Apicurio Registry

**Date**: 2026-09-11 (P34 added; P31-P33 revised; base sweep results from 2026-08-24)
**Source**: 30+ PRs (May-September 2026) + a codebase sweep of 661 test files
**Patterns**: 34 (11 original + 15 from sweep + 2 from clean-room + 2 from PR #9800 + 1 from issue #9327 + 2 from epic #9807 + 1 from PR #9789)

This catalog documents recurring test failure classes and their proven fixes, extracted
from real CI fixes and a full-codebase pattern sweep. Each pattern has a unique ID, labels
for agent-based filtering, and a fix template.

## Labels

Labels classify patterns for targeted agent sweeps. This table is derived from the
`**Labels**:` line of each pattern below, which is the authoritative declaration.

| Label | Meaning | Patterns |
|---|---|---|
| `concurrency` | Breaks under parallel class/method execution | P1, P5, P6, P7, P12, P30 |
| `timing` | Flakes under CI load or slow machines | P2, P4, P9, P17, P21, P24, P27, P29 |
| `lifecycle` | Infra setup/teardown mismanagement | P5, P8, P11, P14, P16, P30 |
| `correctness` | Test passes but does not verify what it claims | P2, P7, P13, P15, P18, P20, P26, P27, P28, P29, P31, P34 |
| `waste` | Burns CI time without improving reliability | P4, P17, P21, P24, P25, P32 |
| `hygiene` | Code quality, maintainability, dead code | P19, P22, P23, P33 |
| `race` | Race condition testing quality | P3, P10, P15, P34 |

## Scoring scope

Every pattern gates the per-PR quality gate unless it appears in the table below. Listing
only the exceptions means a pattern added later gates by default, with no edit here.

| Pattern | Treatment | Why |
|---|---|---|
| P32 | Gating, narrow form only | Score down only when a new `@QuarkusTest` has no CDI injection at all. Most app tests already use `@QuarkusTest` (Pattern 32 carries the count); converting them is backlog work tracked in #9807, not a reason to score down a new test that follows the local convention. |
| P33 | Advisory, never scored | `@ParameterizedTest` adoption is a style preference. Scoring it would penalize a PR for adding two structurally similar test methods, which is the "rule written too broadly" objection raised in review on #8626. |

## Pattern 1: Static Field Cross-Class Contamination
**Labels**: `concurrency`

**Failure class**: Tests pass individually but fail when run concurrently. Symptoms include "already exists" 409s, duplicate key violations, wrong event counts.

**Mechanism**: JUnit 5 with `@TestInstance(PER_CLASS)` and `parallel.mode.classes.default=concurrent` runs multiple test classes in the same JVM simultaneously. Static fields are shared across ALL subclasses, so classes that inherit from a common base stomp on each other's state.

**Fix**: Convert static fields to instance fields. Under `PER_CLASS` lifecycle, instance fields are still shared across methods within one class but isolated between classes.

**Gotcha**: A static counter used for uniqueness (e.g., connector IDs) should stay static (that is what guarantees cross-class uniqueness). The bug is caching its *derived* values in static fields. Capture derived values as instance fields in `@BeforeAll`.

**PRs**: #9798 (Debezium connector names), #9757 (KafkaFacade lifecycle), #8452 (operator ITBase)

---

## Pattern 2: TOCTOU (Check-Then-Act) in Test Assertions
**Labels**: `timing`, `correctness`

**Failure class**: Test polls until a condition is true, then makes a SEPARATE request assuming the condition still holds. Fails intermittently.

**Mechanism**: The probed state is eventually-consistent (e.g., `@Dynamic` config properties, Kafka consumer offsets). The state can revert between the probe and the assertion.

**Fix**: Retry the whole "make the request AND assert on it" as one atomic unit. Use Awaitility or a retry loop around the complete operation, not just the probe.

```java
// BAD: probe, then trust
await().until(() -> probeCompressionDisabled());
response = makeRequest();  // state might have changed!
assertNull(response.header("Content-Encoding"));

// GOOD: retry the whole assertion
await().untilAsserted(() -> {
    Response r = makeRequest();
    assertNull(r.header("Content-Encoding"));
});
```

**PRs**: #9798 (HttpCompressionTest), #8225 (KafkaSQL snapshot assertions)

---

## Pattern 3: Deterministic Race Reproduction with Byteman
**Labels**: `race`

**Failure class**: A race condition exists in production code but is nearly impossible to trigger in tests because it requires precise thread interleaving.

**Mechanism**: Byteman injects thread coordination (freeze/release) at exact bytecode points, forcing the interleaving that triggers the race. The test does not rely on scheduler luck.

**Fix template**: "Freeze + volatile flag + spin" pattern:
1. Byteman rule freezes Thread A at the race window (`waitFor(key, timeout)`)
2. Rule sets a volatile flag (`writerFrozen = true`) BEFORE blocking
3. Thread B spins on the flag (with bail-out timeout), confirming Thread A is frozen
4. Thread B exercises the race, then signals Thread A to resume
5. Assert the observable effect (the flag was set, proving the rule fired)

**Twin test**: Same Byteman rule with the fix applied must make the forced interleaving harmless.

**Infrastructure**: not on `main` yet. The `-Pbyteman` profile arrives with PR #9789. Until that merges, `./mvnw -Pbyteman` names a profile that does not exist. Maven does not fail on that; it prints `[WARNING] The requested profile "byteman" could not be activated because it does not exist` after `BUILD SUCCESS` and exits 0, so the rules never load and the command still looks like it worked.

What the PR branch adds is the profile in `app/pom.xml` plus BMUnit5 4.0.27. Rules are inline: `@WithByteman` on the class, `@BMRules`/`@BMRule` on the method. There are no `.btm` files and no `resourcescript:` to manage, because BMUnit attaches the agent through the JDK Attach API, driven by `-Djdk.attach.allowAttachSelf=true -Dbyteman.agent=true`. `byteman-bmunit5` sits at provided scope outside the profile. That scope satisfies two constraints at once: the test classes still compile without `-Pbyteman`, and FOSSA does not flag the dependency the way it did when the same artifact sat at unconditional test scope. Moving it back to test scope re-breaks the license scan. `@EnabledIfSystemProperty(named="byteman.agent", matches="true")` skips the class when the profile is inactive.

**Key traps**:
- BYTEMAN-38: `signalWake` before `waitFor` loses the signal. Always use `waitFor(key, TIMEOUT)`.
- Rule errors are silent (disabled rule, test "passes" because injection never happened). Always assert an observable effect.
- `signal()` and `setFlag()` do NOT exist. Use `signalWake()` and `flag()`.
- Nested classes: use `Outer$Inner` in `targetClass`.

**PRs**: #9789 (versionOrder race, SELECT FOR UPDATE fix)

---

## Pattern 4: Unbounded Wait / Missing Timeout
**Labels**: `timing`, `waste`

**Failure class**: Test hangs indefinitely in CI (3+ hours), eventually killed by GitHub Actions runner timeout.

**Mechanism**: A wait operation (HTTP request, consumer poll, Kafka Connect readiness) has no timeout or an insufficient one. Under CI load, the operation takes longer than expected and blocks forever.

**Fix**: Add explicit timeouts at every wait layer:
1. **HTTP client**: connect timeout + read-idle timeout (`RegistryClientOptions.requestTimeout(10_000, 60_000)`)
2. **JUnit**: `junit.jupiter.execution.timeout.default=10m`
3. **Job**: `timeout-minutes: 45` on CI jobs
4. **Awaitility**: explicit `atMost()` on every `await()`

**PRs**: #9722 (Vert.x WebClient timeouts, JUnit 10m timeout), #8511 (operator Kafka test timeouts), #8158 (KafkaSQL startup 120s), #8651 (Awaitility timeouts)

---

## Pattern 5: Shared Infrastructure Lifecycle Race
**Labels**: `concurrency`, `lifecycle`

**Failure class**: A test class tears down shared infrastructure (Kafka, Strimzi, database) while another test class is still using it. Symptoms: connection refused, consumer errors, NPEs.

**Mechanism**: Infrastructure is started once (static/shared) but cleanup is per-class. Under parallel execution, Class A's `@AfterAll` tears down the infra while Class B is still running.

**Fix**: Reference-count the shared resource. The first class to request it starts it; the last class to release it stops it. Teardown only on JVM shutdown.

```java
// KafkaFacade: synchronized start/stop with reference counting
private static final AtomicInteger refCount = new AtomicInteger(0);
public synchronized void start() { if (refCount.incrementAndGet() == 1) doStart(); }
public synchronized void stop()  { if (refCount.decrementAndGet() == 0) doStop();  }
```

**Alternative**: Install infrastructure once per JVM in a shared namespace (Strimzi cluster-wide), and give each test class its own namespace for CRs.

**PRs**: #9757 (KafkaFacade ref-counting), #9722 (Strimzi cluster-wide install), #8652 (dedicated Kafka CR resources)

---

## Pattern 6: Resource Registration Collision Under Parallelism
**Labels**: `concurrency`

**Failure class**: Parallel test classes register the same named resource (connector, topic, rule) and get 409 Conflict.

**Mechanism**: Multiple classes use the same resource name (hardcoded or derived from a shared counter) and register concurrently.

**Fix**: Serialize registration with a lock, OR use unique per-class names.

```java
// Debezium: serialize connector register/delete across classes
private static final Object CONNECTOR_LOCK = new Object();
@BeforeAll void setup() {
    synchronized (CONNECTOR_LOCK) {
        registerConnector(uniqueConnectorName);
        waitForConnectorReady();
    }
}
```

**PRs**: #9757 (Debezium connector lock), #9757 (local-converter mount AtomicBoolean)

---

## Pattern 7: Assertion on Exact Count with Background Noise
**Labels**: `concurrency`, `correctness`

**Failure class**: Test asserts `events.size() == 3` but gets 4 or 5 because parallel tests or internal mechanisms (snapshots, admin operations) inject extra events.

**Mechanism**: The count includes events from other test classes running concurrently, or from infrastructure operations (KafkaSQL snapshots, health checks).

**Fix**: Filter by test-specific content before counting.

```java
// BAD: count all events
assertEquals(3, consumeAvroEvents(topic, 3, 60));

// GOOD: count only events with our specific value
assertEquals(3, consumeAvroEvents(topic, 3, 60, insertedValue));
```

**PRs**: #9757 (recovery test event counting)

---

## Pattern 8: Failsafe Rerun in Deployed Environment
**Labels**: `lifecycle`

**Failure class**: Failsafe `rerunFailingTestsCount=1` re-executes failing tests but they all fail with connection-refused on the rerun.

**Mechanism**: The first test plan's `@AfterAll` tears down the deployed namespace. The rerun starts in the same JVM but the infrastructure is gone.

**Fix**: Deploy once per JVM, clean up only on JVM shutdown (shutdown hook), not per-test-plan.

**PRs**: #9722 (RegistryDeploymentManager lifecycle)

---

## Pattern 9: Eventually-Consistent Config in Tests
**Labels**: `timing`

**Failure class**: Test sets a config property and immediately asserts the effect. Fails intermittently.

**Mechanism**: Config properties go through a source chain (DB-backed dynamic config at ordinal 450, System properties at 400) with caching. A `System.setProperty` write may not be visible until the cache refreshes.

**Fix**: Wrap the complete config-change-then-assert cycle in a retry:

```java
System.setProperty("apicurio.feature.enabled", "false");
await().atMost(30, SECONDS).untilAsserted(() -> {
    Response r = callEndpoint();
    assertEquals(404, r.statusCode());
});
```

**PRs**: #9798 (HttpCompressionTest dynamic toggle)

---

## Pattern 10: Mock Object Thread Safety (Stampede Testing)
**Labels**: `race`

**Failure class**: Need to verify that concurrent requests produce exactly N calls to a shared resource (e.g., one token fetch instead of 20).

**Mechanism**: Standard Mockito mocking with `CyclicBarrier` synchronization to ensure all threads start simultaneously, then `AtomicInteger` inside the mock's answer to count invocations. Add artificial latency in the mock to widen the stampede window.

**Fix template**:

```java
AtomicInteger fetchCount = new AtomicInteger(0);
when(mock.getToken(any())).thenAnswer(inv -> {
    fetchCount.incrementAndGet();
    Thread.sleep(200);  // widen the window
    return new Token("mock");
});

CyclicBarrier barrier = new CyclicBarrier(threadCount);
// launch threadCount threads, each awaiting the barrier then calling the method
// assert fetchCount.get() == 1
```

**PRs**: #9790 (OidcTokenStampedeTest with mock-oauth2-server)

---

## Pattern 11: KafkaConsumer Shutdown Race
**Labels**: `lifecycle`

**Failure class**: `ConcurrentModificationException` during application shutdown.

**Mechanism**: `KafkaConsumer` is not thread-safe. `@PreDestroy` calls `close()` from the CDI shutdown thread while the consumer thread is inside `poll()`.

**Fix**: Use `wakeup()` (the only thread-safe method), catch `WakeupException` in the consumer loop, and call `close()` from the consumer thread's own `finally` block. Join the consumer thread with a timeout and interrupt as fallback.

**PRs**: #9791 (KafkaSqlRegistryStorage.onDestroy)

---

## Pattern 12: Shared Mutable DTO
**Labels**: `concurrency`

**Failure class**: Tests pass individually but produce wrong results under parallel method execution or test reordering.

**Mechanism**: Mutable DTO objects (e.g., `CreateArtifact`, `CreateRule`) declared as `private static final` and mutated across tests via setters. The `final` keyword prevents reassignment but not mutation. Each test that calls `setArtifactId()` leaves residue for subsequent tests.

**Fix**: Replace the static field with a factory method called in `@BeforeEach` or at the start of each test method.

**Affected files**: `SimpleAuthTest.java`, `AuthTestLocalRoles.java`

---

## Pattern 13: Assertion-Free Test
**Labels**: `correctness`

**Failure class**: Test always passes. Provides no regression signal.

**Mechanism**: Test methods contain zero assertions and only verify "no exception thrown." A silent behavioral change in the method under test goes undetected.

**Fix**: Add explicit assertions on the method's return value or side effects.

**Affected files**: `MultiRoleValueTest.java`

---

## Pattern 14: Unclosed Test Consumer Leak
**Labels**: `lifecycle`

**Failure class**: Resource leak; under parallel execution, leaked consumers hold group membership and block rebalancing.

**Mechanism**: KafkaConsumer allocated in `@BeforeAll` but never closed. No `@AfterAll`, no try-with-resources.

**Fix**: `@AfterAll static void tearDown() { if (consumer != null) consumer.close(); }`

**Affected files**: `RegistryEventsTest.java`, `KafkaSqlEventsTest.java`

---

## Pattern 15: Shared Consumer Cross-Test Event Bleed
**Labels**: `race`, `correctness`

**Failure class**: Test assertions match events from a different test method. Intermittent false passes and false failures.

**Mechanism**: A single KafkaConsumer reused across all test methods in a `@TestInstance(PER_CLASS)` class. Events accumulate across tests. Overlapping field selectors (same eventType, same groupId pattern) cause cross-test matches.

**Fix**: Add unique correlation IDs per test and filter `lookupEvent` by those IDs. Alternatively, create a new consumer per test method with a fresh group ID.

**Affected files**: `RegistryEventsTest.java`

---

## Pattern 16: Silent Cleanup Failure
**Labels**: `lifecycle`

**Failure class**: Tests fail with stale state from a previous test, but the cleanup failure that caused it is invisible in logs.

**Mechanism**: `@AfterEach` or cleanup code wraps exceptions in `catch (Exception ignored) {}`, hiding failures that leave stale state.

**Fix**: Replace empty catch with `log.warn("Cleanup failed", e)`.

**Affected files**: `ConfluentClientTest.java`, `ReferenceGraphIT.java`, `ArtifactsIT.java`

---

## Pattern 17: Fixed-Sleep Flush Wait
**Labels**: `timing`, `waste`

**Failure class**: Test passes but wastes CI time. Fragile on slow machines.

**Mechanism**: `Thread.sleep(N)` to wait for an eventually-consistent operation (telemetry flush, cache eviction) instead of Awaitility. The sleep is bounded (not P4's unbounded hang), but it wastes CI time.

**Fix**: `await().atMost(N, SECONDS).untilAsserted(() -> { ... })`

**Affected files**: `UsageTelemetryTest.java` (70s total), `ERCacheTest.java`, `ContractRuleLocalEvaluationTest.java`

---

## Pattern 18: Untyped Exception Expectation
**Labels**: `correctness`

**Failure class**: Test passes on any exception (including bugs like NPE or ClassCastException), not just the expected one.

**Mechanism**: Tests use `Assertions.fail()` followed by `catch (Exception ignored) {}` instead of `assertThrows(SpecificException.class, ...)`.

**Fix**: `assertThrows(ExpectedException.class, () -> { ... })`

**Affected files**: `JsonSchemaSerdeTest.java` (15 occurrences), `AvroSerdeTest.java`

---

## Pattern 19: Reflective CDI Bypass
**Labels**: `hygiene`

**Failure class**: Test breaks at runtime (not compile time) when the target field is renamed or retyped.

**Mechanism**: `Field.setAccessible(true)` to inject mocks into CDI-managed fields, bypassing the type system.

**Fix**: Switch to constructor injection or `@InjectMock` (Quarkus) / `@Mock` + `@InjectMocks` (Mockito).

**Affected files**: `OdcsTagProjectorTest.java`, `QualityScoreCalculatorTest.java`, `PromotionServiceTest.java`

---

## Pattern 20: Silent Validation Sink
**Labels**: `correctness`

**Failure class**: Schema validation appears tested but actually never fails the test.

**Mechanism**: A validation helper catches `ValidationException`, prints it to stdout, and returns normally. The calling test never asserts the result.

**Fix**: Propagate the exception or return a boolean; assert the result in the calling test.

**Affected files**: `JsonSchemaSerdeTest.java`

---

## Pattern 21: Redundant Pre-Wait
**Labels**: `timing`, `waste`

**Failure class**: Test passes but wastes CI time proportional to the number of artifact operations.

**Mechanism**: Fixed `Thread.sleep()` immediately before a retry/await loop that already handles eventual consistency. The sleep adds latency without improving reliability.

**Estimated CI waste**: ~1s per createArtifact call across all 41 IT classes; 12s in SearchIT alone.

**Fix**: Remove the `Thread.sleep()` call; the subsequent retry loop handles the wait.

**Affected files**: `ApicurioRegistryBaseIT.java` (inherited by 41 classes), `SearchIT.java` (4 instances)

---

## Pattern 22: Orphaned Disabled Test
**Labels**: `hygiene`

**Failure class**: Dead code that accumulates silently. No path to resolution.

**Mechanism**: Test classes or methods marked `@Disabled` with a textual reason but no link to a GitHub issue. Without a tracking issue, disabled tests become permanently abandoned.

**Fix**: File a GitHub issue for each, add the issue URL: `@Disabled("Reason - see #NNNN")`. Delete if redesign is not planned.

**Affected files**: `LoadIT.java`, `DoNotPreserveIdsImportIT.java`, `GenerateCanonicalHashImportIT.java`, `AvroSerdeIT.java`, `DebugTest.java`

---

## Pattern 23: Unbounded Recursive Retry
**Labels**: `hygiene`

**Failure class**: `StackOverflowError` instead of a diagnostic failure message.

**Mechanism**: A method catches any Exception and calls itself recursively with no retry counter or depth limit.

**Fix**: Add a `retryCount` parameter; fail with a clear message after N attempts.

**Affected files**: `ITBase.java`

---

## Pattern 24: Sleep-Then-Assert-Stable
**Labels**: `timing`, `waste`

**Failure class**: Test passes but adds minutes of unconditional sleep per run.

**Mechanism**: `Thread.sleep(30-60s)` followed by assertions that "nothing changed." Used for negative assertions (no upgrade occurred, no downgrade happened). Only checks the condition once at the end of the window.

**Fix**: `await().during(ofSeconds(N)).atMost(maxDuration).untilAsserted(...)` polls continuously and fails fast if the condition breaks during the window.

**Affected files**: `UpgradeOLMITTest.java` (3 occurrences; 2.5 minutes total)

---

## Pattern 25: Benchmark Masquerading as Test
**Labels**: `waste`

**Failure class**: CI time wasted on non-test code with no assertions.

**Mechanism**: Benchmark methods annotated with `@Test` running millions of iterations, printing results to stdout only.

**Fix**: Exclude from surefire via `@Tag("benchmark")` and a surefire `excludedGroups` configuration, or migrate to JMH.

**Affected files**: `SerDesTracerBenchmark.java`

---

## Pattern 26: Existence-Only Assertion
**Labels**: `correctness`

**Failure class**: Test verifies connectivity but not correctness. A regression that changes returned data goes undetected.

**Mechanism**: Tests that only use `assertNotNull` without checking the actual content of returned objects.

**Fix**: Add value assertions (e.g., `assertEquals(expectedGroupId, result.getGroupId())`).

**Affected files**: `McpAuthenticationTest.java`

---

## Pattern 27: Side Effects Inside Awaitility Lambda
**Labels**: `correctness`, `timing`

**Failure class**: Test creates duplicate resources or corrupts state on retry. Intermittent 409 Conflict or constraint violations.

**Mechanism**: Awaitility retries the entire lambda on assertion failure. If the lambda contains side effects (create, insert, register), those side effects execute on every retry, not just once.

**Fix**: Separate the mutating operation from the polling assertion. Execute the mutation once before the `await()`, then poll for its effect.

```java
// BAD: createArtifact runs on every retry
await().untilAsserted(() -> {
    createArtifact(groupId, artifactId);
    assertNotNull(getArtifact(groupId, artifactId));
});

// GOOD: create once, poll for result
createArtifact(groupId, artifactId);
await().untilAsserted(() -> {
    assertNotNull(getArtifact(groupId, artifactId));
});
```

---

## Pattern 28: Awaitility Error Swallowing
**Labels**: `correctness`

**Failure class**: Test passes when it should fail. Errors inside the polling lambda are silently swallowed.

**Mechanism**: `await().until(() -> condition)` swallows exceptions thrown inside the lambda and treats them as "condition not yet true" (retries). If the exception is a real bug (NPE, ClassCastException), the test waits until timeout and reports "condition not met" instead of the actual error.

**Fix**: Use `untilAsserted()` instead of `until()` when the lambda contains assertions or can throw unexpected exceptions. `untilAsserted` propagates assertion errors on the last attempt.

```java
// BAD: NPE inside until() is swallowed, test times out
await().atMost(10, SECONDS).until(() -> getResult().getName().equals("expected"));

// GOOD: NPE propagates on last retry
await().atMost(10, SECONDS).untilAsserted(() -> {
    assertEquals("expected", getResult().getName());
});
```

---

## Pattern 29: Async-Treated-as-Sync
**Labels**: `timing`, `correctness`

**Failure class**: Test passes individually but fails under parallel execution or CI load. Symptoms: resource collision from a previous test's teardown still in flight, "already exists" errors on resources that should have been deleted.

**Mechanism**: An asynchronous operation (Kubernetes namespace delete, container stop, async HTTP POST) is treated as complete because the API call returned successfully. The caller proceeds to the next step without waiting for the operation to actually finish. Under parallel execution, the next test's setup collides with the previous test's still-in-flight teardown.

**Example**: `ITBase.afterAll()` checked `assertThat(client.namespaces().delete()).isNotNull()` and returned. Kubernetes namespace deletion is asynchronous; the API server removes the namespace object only after all resources inside it (including Ingress hostnames) are garbage-collected. The next Auth test's Ingress creation raced against the stale namespace's deletion.

**Fix**: Poll for the operation's actual completion before returning.

```java
// BAD: treat API acceptance as completion
assertThat(client.namespaces().withName(ns).delete()).isNotNull();

// GOOD: wait for actual termination
client.namespaces().withName(ns).delete();
await().atMost(60, SECONDS)
    .until(() -> client.namespaces().withName(ns).get() == null);
```

**PRs**: #9800 (operator namespace teardown race)

---

## Pattern 30: Third-Party Thread Leak
**Labels**: `concurrency`, `lifecycle`

**Failure class**: `ConcurrentModificationException` or `KafkaConsumer is not safe for multi-threaded access` errors in test N+1 after test N timed out. Cascading failures across all subsequent tests in the class.

**Mechanism**: A utility library (e.g., `org.rnorth.ducttape.Unreliables`) submits retry loops to a shared daemon thread pool. On timeout, `future.get(timeout, unit)` throws, but the submitted Runnable is never cancelled. The leaked thread continues to access shared mutable state (e.g., a KafkaConsumer field) after the caller has moved on. When the next test reassigns that field, the leaked thread and the new test collide.

**Detection signal**: any use of `Unreliables.retryUntilTrue` or `retryUntilSuccess` with a shared mutable resource (consumer, producer, connection) as the retry target.

**Fix**: Replace the library call with a caller-thread poll loop using an explicit deadline. No background threads, no leaked futures.

```java
// BAD: Unreliables leaks the retry thread on timeout
Unreliables.retryUntilTrue(timeout, SECONDS, () -> {
    ConsumerRecords<?, ?> records = consumer.poll(Duration.ofMillis(500));
    return records.count() >= expected;
});

// GOOD: poll on the calling thread with a deadline
long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeout);
int total = 0;
while (total < expected && System.currentTimeMillis() < deadline) {
    ConsumerRecords<?, ?> records = consumer.poll(Duration.ofMillis(500));
    total += records.count();
}
assertTrue(total >= expected, "Expected " + expected + " records, got " + total);
```

**PRs**: #9800 (Debezium Unreliables thread leak)

---

## Pattern 31: Vacuous Predicate
**Labels**: `correctness`

**Failure class**: Test always passes regardless of the actual result.

**Mechanism**: Two shapes. `Stream.allMatch()` and `noneMatch()` return `true` on an empty collection, so a test that filters to an empty set and asserts `allMatch(condition)` passes vacuously. The same failure appears without a collection whenever the failure condition is unreachable: `assertTrue(t.isInterrupted() || !t.isAlive())` is satisfied by both states of a thread, so no thread can fail it.

**Fix**: For the collection form, assert the collection is non-empty before applying the predicate. For the unreachable-condition form, work out which concrete state the test is trying to prove and assert that one state. If a disjunction is genuinely needed, assert the specific value that distinguishes pass from fail alongside it.

**Issues**: #9327 (SearchCommandTest)

---

## Pattern 32: QuarkusTest Overuse
**Labels**: `waste`

**Failure class**: Test suite is slow. No correctness issue.

**Mechanism**: Tests that only exercise pure logic use `@QuarkusTest`, booting the full runtime. 192 of 270 app tests (71%) use `@QuarkusTest`; 66 distinct profiles cause 66 restarts.

**Fix**: Convert pure-logic tests to plain JUnit. Detection: test uses `@QuarkusTest` but has no CDI injection.

**Source**: epic #9807

---

## Pattern 33: ParameterizedTest Underuse
**Labels**: `hygiene`

**Failure class**: Test maintenance burden. Copy-paste variants diverge.

**Mechanism**: Multiple test methods with identical structure, varying one argument. Only 2.7% of tests use `@ParameterizedTest`.

**Fix**: Convert to `@ParameterizedTest` with `@MethodSource` or `@EnumSource`.

**Source**: epic #9807

---

## Pattern 34: Concurrency Fix Without Race Test
**Labels**: `correctness`, `race`

**Failure class**: Silent. The fix compiles and CI passes, but the race it addresses is never tested. A later refactor can reintroduce the same bug undetected.

**Mechanism**: A PR fixes a concurrency bug (TOCTOU, lost update, duplicate key under contention) by changing production code (`ConcurrentHashMap`, `volatile`, `synchronized`, `AtomicReference`, SQL UPSERT replacing DELETE+INSERT) and adds zero test files. The race window is too narrow for a `Thread.sleep`-based test, so no test gets written at all.

**Detection signal**: the diff touches concurrency primitives or replaces a check-then-act sequence, and no test in the diff exercises the race. The runnable form of this check lives in Phase 1 of `/apicurio-test-quality`, which owns the pattern list; do not keep a second copy of the regex here. Note that an empty `src/test/` list is a sufficient condition, not a necessary one: a diff carrying an unrelated test edit still matches this pattern.

**Fix**: Start with what runs on the current base. For SQL races, a `@QuarkusTest` driving two concurrent transactions against the same row is enough. For in-process races, coordinate the threads with a `CyclicBarrier` or `CountDownLatch` so the interleaving is forced rather than hoped for. Reach for a Byteman rule only when no barrier can open the window, and check Pattern 3's Infrastructure note first: on a base where `-Pbyteman` does not exist, a Byteman test is not a fix, it is a test that never runs. Whichever you pick, prove it by mutation: revert the production fix and show the test red.

The worked example to copy is `app/src/test/java/io/apicurio/registry/storage/impl/sql/ConcurrentVersionCreationTest.java`, which arrives with PR #9789. Four things in it are worth carrying to any new rule, and all four are easy to get wrong:

- `targetClass` takes a fully qualified name as a string. Byteman does not resolve imports, so a bare class name matches nothing and the rule is silently disabled.
- Both rules fire `AT ENTRY`. A call-site location such as `AFTER INVOKE java.util.Map.containsKey 1` rots as soon as anyone edits the method body, and a rule with no valid injection point fails silently.
- The freeze is released from the *second* thread's entry, not from any exit of the frozen thread. The frozen thread is parked in `waitFor`, so it cannot release itself.
- Flag names are namespaced with the test class (`ConcurrentVersionCreationTest.writer-entered`). Byteman flags are JVM-global and collide across test classes otherwise.

The test also arms its rules with a system property only after fixture setup, so the freeze rule does not fire during the fixture's own call into the target method.

**PRs**: #9789 (a Byteman race test that passed against the unfixed code until review caught it), #9793 (UPSERT fix whose accompanying test asserted idempotency but never exercised the race)

---

## Cross-Cutting: Test Infrastructure Investments

These are not patterns per se but infrastructure decisions that enabled the fixes above. The
Status column matters: an investment that has not landed on `main` cannot be relied on by a
new test.

| Investment | What it enables | PR | Status |
|---|---|---|---|
| JUnit 5 class-level parallelism | Patterns 1, 5, 6, 7 | #9757 | merged |
| Byteman `-Pbyteman` profile | Patterns 3, 34 | #9789 | open, not on `main` |
| mock-oauth2-server | Pattern 10 | #9790 | open, not on `main` |
| Awaitility everywhere | Patterns 2, 4, 9 | #8651, #8388 | merged |
| Surefire `rerunFailingTestsCount` | Pattern 8 (safety net) | #8388 | merged |
| Per-JVM infrastructure lifecycle | Patterns 5, 8 | #9722 | merged |
