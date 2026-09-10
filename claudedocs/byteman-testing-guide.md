# Byteman: Deterministic Concurrency Testing for Apicurio Registry

## What is Byteman?

Byteman is a JVM bytecode injection tool that lets you insert Java code into any method (yours, a library's, the JDK's) at runtime, without recompiling. Among the things you can inject: thread-blocking and thread-releasing operations that let you control exactly when a thread pauses and when it resumes.

This makes it the right tool when the question is: "what happens if thread B reads exactly while thread A is stopped at this point?" Race conditions that are nearly impossible to trigger in normal testing become deterministic, reproducible, and fast.

Byteman is a Red Hat project (Andrew Dinn), actively maintained, Apache 2.0 licensed, and works on JDK 21.

## Why not the alternatives?

| Tool | What it does | Why it's not enough for us |
|---|---|---|
| **Lincheck** (JetBrains) | Model-checks isolated concurrent data structures | Right for verifying linearizability of a queue or map. Wrong for injecting scheduling into an application's own flow (Quarkus + JDBC + CDI). |
| **JCStress** (OpenJDK) | Memory-model micro-tests | Too low level. We need to freeze inside `createArtifactVersion`, not test `volatile` visibility. |
| **vmlens** | Detects races that happen to be observed during a run | It detects; it does not force. We need the race to happen 100% of the time, not hope to observe it. |
| **CyclicBarrier + Thread.sleep** | Coordinates test threads at a coarse level | Good for stampede tests (see `OidcTokenStampedeTest`), but cannot freeze a thread at a precise bytecode point inside production code. |

Byteman fills the gap: it freezes a thread at an exact point in production code, by construction, every time.

## How it works: ECA rules

A Byteman rule says: **when** execution reaches a point (Event), **if** a condition is true (Condition), **do** these expressions (Action).

The event can be `AT ENTRY`, `AT EXIT`, `AT LINE n`, `AT INVOKE method`, and more. Actions can read/write fields, call methods, sleep, or block the thread.

Here is the rule we use to test the `createArtifactVersion` race:

```
RULE freeze first writer
CLASS io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage
METHOD createArtifactVersion(...)
AT ENTRY
IF NOT flagged("writer-entered")
DO flag("writer-entered");
   java.lang.System.setProperty("byteman.writerFrozen", "true");
   waitFor("versionOrder-race", 10000)
ENDRULE

RULE release frozen writer
CLASS io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage
METHOD createArtifactVersion(...)
AT EXIT
IF flagged("writer-entered") AND NOT flagged("writer-released")
DO flag("writer-released");
   signalWake("versionOrder-race", true)
ENDRULE
```

Rule 1 fires on the first thread to enter the method, sets a system property (so the test can observe it), and blocks the thread with `waitFor`. Rule 2 fires when a second thread exits the same method, releasing the first thread with `signalWake`.

The result: Thread A is frozen before it touches the database, Thread B creates its version freely, and only then Thread A resumes. This is not a race you hope to trigger. It is a race you construct.

## What we tested with it

### The versionOrder duplicate bug (#9775)

`createArtifactVersion` computed the next `versionOrder` via `MAX(versionOrder)` without holding a row-level lock. Two concurrent version creations for the same artifact could both read `MAX = 5` and both insert `versionOrder = 6`. Silent data corruption.

The fix adds `SELECT ... FOR UPDATE` to serialize concurrent version creation at the database level. The Byteman test proves the fix works:

1. Thread A enters `createArtifactVersion` and is frozen by the Byteman rule
2. Thread B creates a version concurrently (gets `versionOrder = 2`)
3. Thread A is released and creates its version (gets `versionOrder = 3`, not 2)
4. The test asserts both versions have distinct `versionOrder` values

Without the fix, both threads would get `versionOrder = 2`. The test fails deterministically before the fix and passes deterministically after it.

## The test pattern: freeze + volatile flag + spin

The pattern has three components that work together:

**Byteman rule**: freezes a thread at the race window and sets a flag before blocking.

**Test reader thread**: spins on the flag (with a bail-out timeout), confirming the writer is frozen before exercising the race.

**Observable effect assertion**: the test asserts the flag was set, proving the Byteman rule fired. Without this, a broken rule (typo in class name, method signature changed) would silently disable the injection, and the test would "pass" because the race was never attempted.

```java
// Thread B spins until Thread A is frozen
long deadline = System.currentTimeMillis() + 5000;
while (!"true".equals(System.getProperty("byteman.writerFrozen"))) {
    Thread.sleep(50);
    if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("Timed out waiting for Byteman rule to fire");
    }
}

// Assert the rule actually fired
assertEquals("true", System.getProperty("byteman.writerFrozen"),
    "Byteman rule should have set the writerFrozen flag");
```

### The fix validation twin

The same test with the fix applied must pass. The Byteman rule still tries to force the interleaving, but the `SELECT ... FOR UPDATE` lock makes the interleaving harmless. This is the real value over detection tools: you do not have to wait for the race to happen again to know your fix works.

## How to run it

```bash
# Run the Byteman concurrency test
./mvnw test -pl :apicurio-registry-app -Pbyteman -Dtest=ConcurrentVersionCreationTest

# Run with a different Byteman script
./mvnw test -pl :apicurio-registry-app -Pbyteman -Dbyteman.script=byteman/other-test.btm -Dtest=OtherTest
```

The `-Pbyteman` Maven profile:
- Adds `org.jboss.byteman:byteman:4.0.27` as a test dependency
- Loads the Byteman agent via `-javaagent` in the surefire argLine
- Sets `byteman.agent=true` so `@EnabledIfSystemProperty` gates the test

Without `-Pbyteman`, the test is skipped. The normal test suite is unaffected.

## How to write a new Byteman test

1. **Find the window**: identify the "publishes partial state, then completes state" sequence in the production code. For `createArtifactVersion`, it was: read `MAX(versionOrder)`, then insert the new version.

2. **Write the freeze rule** (`.btm` file in `src/test/resources/byteman/`):
   - Target the class and method where the race happens
   - Use `AT ENTRY` to freeze before any work, or `AT INVOKE` / `AT LINE` for a more precise point
   - Use `flagged()` guards so only the first thread is frozen
   - Set an observable flag (system property) before calling `waitFor`
   - Use `waitFor(key, TIMEOUT)` with a timeout to keep the test finite

3. **Write the test** (`@QuarkusTest`, `@EnabledIfSystemProperty`):
   - Thread A calls the production method (gets frozen by the rule)
   - Thread B spins on the observable flag, then exercises the race
   - Assert the observable effect (rule fired) and the correctness invariant (no duplicates)

4. **Verify both directions**:
   - Before the fix: the test fails deterministically (the race causes the invariant violation)
   - After the fix: the test passes deterministically (the fix makes the forced interleaving harmless)

## Traps we learned from

**Early signals are lost (BYTEMAN-38).** If `signalWake(key)` fires before anyone is in `waitFor(key)`, the signal is lost and the wait blocks forever. Always use `waitFor(key, TIMEOUT)` and treat the timeout as a test failure.

**Rule errors do not fail the build.** A mistyped class name or method signature disables the rule silently. The test "passes" because the injection never happened. Always assert an observable effect of the injection in the test (the `writerFrozen` flag check).

**`signal()` and `setFlag()` do not exist.** The correct names are `signalWake()` and `flag()`. Use `-Dorg.jboss.byteman.verbose=true` during development to see rule parsing and typechecking output.

**Nested classes.** Use `Outer$Inner` syntax for inner class names in the rule CLASS field.

## Project integration

| Component | Location |
|---|---|
| Byteman rules | `app/src/test/resources/byteman/*.btm` |
| Byteman tests | `app/src/test/java/.../ConcurrentVersionCreationTest.java` |
| Maven profile | `app/pom.xml`, `<profile id="byteman">` |
| Script path property | `-Dbyteman.script=byteman/<name>.btm` |
| Test gate | `@EnabledIfSystemProperty(named = "byteman.agent", matches = "true")` |

## References

- Byteman homepage: https://byteman.jboss.org/ (includes the Programmer's Guide PDF)
- GitHub: https://github.com/bytemanproject/byteman
- Current version: 4.0.27 (verified on JDK 21, works with Quarkus)
- PR #9789: the versionOrder race fix and Byteman test
