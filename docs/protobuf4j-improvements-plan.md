# Protobuf4j Improvements Implementation Plan

This document outlines the implementation plan for further optimizations and improvements leveraging protobuf4j capabilities in Apicurio Registry.

## Overview

The protobuf4j migration is complete with significant benefits already realized:
- 79% dependency size reduction
- 73% code reduction
- 100% protoc compatibility

This plan addresses 10 additional improvements to further optimize performance, maintainability, and code quality.

## Implementation Status Summary

| Phase | Task | Status |
|-------|------|--------|
| 1.1 | Centralize Well-Known Type Detection | ✅ Complete |
| 1.2 | Clarify Original vs Normalized Proto Text | ✅ Complete |
| 2.1 | Implement ZeroFs FileSystem Pooling | ✅ Complete |
| 2.2 | Implement Protobuf WASM Instance Caching | ✅ Complete |
| 2.3 | Optimize Well-Known Types Loading | ✅ Complete (via pooling) |
| 3.1 | Improve Compatibility Checker Integration | ✅ Complete |
| 3.2 | Implement Reference Rewriting in Dereferencer | ✅ Complete |
| 4.1 | Remove Unused Binary Format Detection | ✅ Complete |
| 4.2 | Enhance Error Messages from WASM | ✅ Complete |
| 5.1 | Implement Batch Schema Compilation | ⏳ Future |

**Tests:** All 144 unit tests pass (98 in protobuf-schema-utilities + 46 in schema-util/protobuf)

### Remaining Items
- Performance benchmarks for pooling improvements
- Memory profiling for cached WASM instances
- Unit tests for `rewriteReferences()` scenarios
- Error message format documentation
- Address long-running server issues (see [Known Issues](#known-issues-for-long-running-servers) below)

---

## Phase 1: Code Quality and Quick Wins

### 1.1 Centralize Well-Known Type Detection

**Priority:** High | **Effort:** Low | **Risk:** Low

**Problem:**
Well-known type filtering logic is duplicated across 6+ files with slight variations, creating maintenance burden and potential inconsistencies.

**Files Affected:**
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaLoader.java`
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaUtils.java`
- `schema-util/protobuf/src/main/java/io/apicurio/registry/protobuf/content/canon/ProtobufContentCanonicalizer.java`
- `serdes/generic/serde-common-protobuf/src/main/java/io/apicurio/registry/serde/protobuf/ProtobufSchemaParser.java`

**Implementation:**

1. Create new utility class:
   ```
   utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufWellKnownTypes.java
   ```

2. Define centralized methods:
   ```java
   public final class ProtobufWellKnownTypes {

       // Google Protocol Buffer well-known types (handled by protobuf4j internally)
       private static final String GOOGLE_PROTOBUF_PREFIX = "google/protobuf/";

       // Google API types (loaded from resources by ProtobufSchemaLoader)
       private static final String GOOGLE_TYPE_PREFIX = "google/type/";

       // Apicurio custom bundled types
       private static final String METADATA_PREFIX = "metadata/";
       private static final String ADDITIONAL_TYPES_PREFIX = "additionalTypes/";

       public static boolean isGoogleProtobufType(String fileName) { ... }
       public static boolean isGoogleApiType(String fileName) { ... }
       public static boolean isApicurioBundledType(String fileName) { ... }
       public static boolean isWellKnownType(String fileName) { ... }
       public static boolean isHandledByProtobuf4j(String fileName) { ... }
       public static boolean shouldSkipAsReference(String fileName) { ... }
   }
   ```

3. Update all affected files to use the centralized utility

4. Add unit tests for all utility methods

**Acceptance Criteria:**
- [x] New `ProtobufWellKnownTypes` utility class created
- [x] All duplicate well-known type checks replaced with utility calls
- [x] Unit tests with 100% coverage of utility methods (39 tests)
- [x] All existing tests pass

---

### 1.2 Clarify Original vs Normalized Proto Text

**Priority:** Medium | **Effort:** Low | **Risk:** Low

**Problem:**
`ProtobufSchema.toProtoText()` returns either original text OR normalized text, which can be confusing for callers.

**File Affected:**
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchema.java`

**Implementation:**

1. Rename internal field:
   ```java
   private String cachedProtoText;      // Stores either original or generated
   private boolean hasOriginalText;     // Tracks if original was provided
   ```

2. Add explicit methods:
   ```java
   /**
    * Returns the original .proto text if available, otherwise generates from FileDescriptor.
    * For canonicalization purposes, use {@link #toNormalizedProtoText()} instead.
    */
   public String toProtoText() { ... }

   /**
    * Returns the normalized/canonical .proto text generated via protobuf4j.
    * This ensures consistent representation for content hashing.
    */
   public String toNormalizedProtoText() { ... }

   /**
    * Returns true if this schema was created with original .proto text.
    */
   public boolean hasOriginalProtoText() { ... }
   ```

3. Update callers that specifically need normalized text (e.g., `ProtobufContentCanonicalizer`)

**Acceptance Criteria:**
- [x] New methods added to `ProtobufSchema` (`toNormalizedProtoText()`, `hasOriginalProtoText()`)
- [x] `ProtobufContentCanonicalizer` uses centralized `ProtobufWellKnownTypes`
- [x] Serdes layer continues using `toProtoText()` for backward compatibility
- [x] All existing tests pass

---

## Phase 2: Performance Optimizations

### 2.1 Implement ZeroFs FileSystem Pooling

**Priority:** Medium | **Effort:** Medium | **Risk:** Medium

**Problem:**
A new virtual filesystem is created for every schema compilation, causing unnecessary object allocation and GC pressure.

**Files Affected:**
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaLoader.java`
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaUtils.java`
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufFile.java`

**Implementation:**

1. Create filesystem pool manager:
   ```
   utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufCompilationContext.java
   ```

2. Design the pool:
   ```java
   public class ProtobufCompilationContext implements AutoCloseable {
       private static final int POOL_SIZE = 4;
       private static final BlockingQueue<FileSystem> pool = new ArrayBlockingQueue<>(POOL_SIZE);

       private final FileSystem fs;
       private final Path workDir;
       private final boolean fromPool;

       public static ProtobufCompilationContext acquire() { ... }

       public Path getWorkDir() { return workDir; }

       public void reset() {
           // Clear all user files, keep well-known types
       }

       @Override
       public void close() {
           if (fromPool) {
               reset();
               pool.offer(fs);
           } else {
               fs.close();
           }
       }
   }
   ```

3. Pre-populate pool with well-known types on first access

4. Update `ProtobufSchemaLoader.loadSchema()` to use pool:
   ```java
   try (ProtobufCompilationContext ctx = ProtobufCompilationContext.acquire()) {
       // Write user protos
       // Compile with protobuf4j
   }
   ```

**Considerations:**
- Thread safety: Each pooled filesystem should only be used by one thread at a time
- Cleanup: Ensure user files are removed but well-known types preserved
- Fallback: Create new filesystem if pool is exhausted

**Acceptance Criteria:**
- [x] `ProtobufCompilationContext` pool implemented
- [x] Well-known types loaded once per filesystem instance
- [x] Thread-safe pool access
- [ ] Benchmark showing improved throughput (target: 2x for repeated compilations)
- [x] All existing tests pass

---

### 2.2 Implement Protobuf WASM Instance Caching

**Priority:** Medium | **Effort:** Medium | **Risk:** Medium

**Problem:**
A new `Protobuf` WASM instance is created for every operation, incurring WASM cold-start overhead.

**Files Affected:**
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaLoader.java`
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaUtils.java`
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufFile.java`

**Implementation:**

1. Extend `ProtobufCompilationContext` to include `Protobuf` instance:
   ```java
   public class ProtobufCompilationContext implements AutoCloseable {
       private final FileSystem fs;
       private final Path workDir;
       private final Protobuf protobuf;  // Cached WASM instance

       public Protobuf getProtobuf() { return protobuf; }
   }
   ```

2. The `Protobuf` instance is tied to its working directory, so each pooled filesystem gets its own cached instance

3. Update all compilation sites to use the cached instance:
   ```java
   try (ProtobufCompilationContext ctx = ProtobufCompilationContext.acquire()) {
       List<FileDescriptor> fds = ctx.getProtobuf().buildFileDescriptors(protoFiles);
   }
   ```

**Considerations:**
- Protobuf instance lifecycle must match filesystem lifecycle
- Thread safety: Instance should not be shared across threads
- Memory usage: Monitor heap impact of cached WASM instances

**Acceptance Criteria:**
- [x] `Protobuf` instance cached within `ProtobufCompilationContext`
- [x] WASM cold-start only occurs once per pool slot
- [ ] Benchmark showing 5-10x improvement for `normalizeSchemaToText()` calls
- [ ] Memory profiling shows acceptable heap usage
- [x] All existing tests pass

---

### 2.3 Optimize Well-Known Types Loading

**Priority:** Low | **Effort:** Medium | **Risk:** Low

**Problem:**
Google API protos are read from classpath resources and written to filesystem on every compilation.

**File Affected:**
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaLoader.java`

**Implementation:**

1. Cache proto content in memory on first load:
   ```java
   private static final Map<String, byte[]> CACHED_PROTO_CONTENT = new ConcurrentHashMap<>();

   private static byte[] getProtoContent(String protoPath) {
       return CACHED_PROTO_CONTENT.computeIfAbsent(protoPath, path -> {
           try (InputStream is = ProtobufSchemaLoader.class.getResourceAsStream("/" + path)) {
               return is.readAllBytes();
           }
       });
   }
   ```

2. When using filesystem pool, keep well-known types pre-written

3. Only write well-known types on first use of a pooled filesystem

**Note:** This improvement is largely superseded by Phase 2.1 (filesystem pooling). If pooling is implemented, well-known types only need to be written once per pool slot.

**Acceptance Criteria:**
- [x] Proto content cached in memory (via `ProtobufCompilationContext` pooling)
- [x] Reduced I/O operations per compilation (well-known types written once per pool slot)
- [ ] Memory impact measured and documented

---

## Phase 3: Architecture Improvements

### 3.1 Improve Compatibility Checker Integration

**Priority:** High | **Effort:** Medium | **Risk:** Medium

**Problem:**
The compatibility checker re-parses schemas from strings even when `FileDescriptor` may already be available from upstream processing.

**Files Affected:**
- `schema-util/protobuf/src/main/java/io/apicurio/registry/protobuf/rules/compatibility/ProtobufCompatibilityChecker.java`
- `schema-util/protobuf/src/main/java/io/apicurio/registry/protobuf/rules/compatibility/protobuf/ProtobufCompatibilityCheckerLibrary.java`

**Implementation:**

1. Add overloaded method accepting pre-parsed schemas:
   ```java
   public CompatibilityExecutionResult testCompatibility(
           CompatibilityLevel compatibilityLevel,
           List<ProtobufFile> existingSchemas,
           ProtobufFile proposedSchema) {
       // Direct comparison without re-parsing
   }
   ```

2. Modify the string-based method to call the new method:
   ```java
   @Override
   public CompatibilityExecutionResult testCompatibility(
           CompatibilityLevel compatibilityLevel,
           List<TypedContent> existingArtifacts,
           TypedContent proposedArtifact,
           Map<String, TypedContent> resolvedReferences) {

       // Parse once
       List<ProtobufFile> existing = existingArtifacts.stream()
           .map(tc -> parseOrThrow(tc))
           .collect(Collectors.toList());
       ProtobufFile proposed = parseOrThrow(proposedArtifact);

       // Delegate to optimized method
       return testCompatibility(compatibilityLevel, existing, proposed);
   }
   ```

3. Consider accepting `FileDescriptor` directly for even tighter integration

**Future Enhancement:**
If the storage layer caches parsed `FileDescriptor` objects, the compatibility checker could accept them directly, avoiding all re-parsing.

**Acceptance Criteria:**
- [x] New overloaded method accepting pre-parsed schemas
- [x] Existing API maintained for backward compatibility
- [x] Reduced parsing in validation+compatibility workflows
- [x] All existing compatibility tests pass

---

### 3.2 Implement Reference Rewriting in Dereferencer

**Priority:** Low | **Effort:** High | **Risk:** Medium

**Problem:**
`ProtobufDereferencer.rewriteReferences()` is not implemented, limiting schema portability features.

**File Affected:**
- `schema-util/protobuf/src/main/java/io/apicurio/registry/protobuf/content/dereference/ProtobufDereferencer.java`

**Implementation:**

1. Parse the schema to identify import statements:
   ```java
   Set<String> imports = ProtobufSchemaUtils.extractImports(content);
   ```

2. Rewrite import paths in the raw text:
   ```java
   String rewritten = content;
   for (Map.Entry<String, String> mapping : resolvedReferenceUrls.entrySet()) {
       String oldPath = mapping.getKey();
       String newPath = mapping.getValue();
       rewritten = rewriteImport(rewritten, oldPath, newPath);
   }
   ```

3. Optionally re-normalize using protobuf4j to ensure valid syntax

**Considerations:**
- Import statement format variations: `import "path"`, `import 'path'`, `import public "path"`
- Preserving comments and formatting in non-import lines
- Handling nested imports in dependencies

**Acceptance Criteria:**
- [x] `rewriteReferences()` implemented
- [x] Handles all import statement formats (`import "path"`, `import 'path'`, `import public "path"`, `import weak "path"`)
- [ ] Unit tests for various rewriting scenarios
- [ ] Integration test with actual schema rewriting workflow

---

## Phase 4: Code Cleanup

### 4.1 Remove Unused Binary Format Detection

**Priority:** Low | **Effort:** Low | **Risk:** Low

**Problem:**
`ProtobufFile.validateSyntaxOnly()` attempts to detect binary (base64-encoded) format before text validation, but this may be unnecessary with protobuf4j.

**File Affected:**
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufFile.java`

**Implementation:**

1. Analyze usage patterns to confirm binary format is not used in `validateSyntaxOnly()` path

2. If confirmed unused, simplify the method:
   ```java
   public static void validateSyntaxOnly(String data) throws IOException {
       if (data == null || data.trim().isEmpty()) {
           throw new IOException("Empty protobuf content");
       }

       // Use protobuf4j's validateSyntax directly
       FileSystem fs = ZeroFs.newFileSystem(...);
       try (FileSystem ignored = fs) {
           Path workDir = fs.getPath(".");
           Files.write(workDir.resolve("schema.proto"), data.getBytes(UTF_8));

           try (Protobuf protobuf = Protobuf.builder().withWorkdir(workDir).build()) {
               ValidationResult result = protobuf.validateSyntax("schema.proto");
               if (!result.isValid()) {
                   throw new IOException("Invalid protobuf syntax: " + result.getErrors());
               }
           }
       }
   }
   ```

3. Keep binary format support in `parseProtoString()` for backward compatibility

**Acceptance Criteria:**
- [x] Usage analysis completed
- [x] Simplified to use `ProtobufCompilationContext` directly (binary detection kept in `parseProtoString()`)
- [x] All existing tests pass

---

### 4.2 Enhance Error Messages from WASM

**Priority:** Low | **Effort:** Low | **Risk:** Low

**Problem:**
WASM protoc errors are wrapped but detailed error context may be lost.

**File Affected:**
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaLoader.java`

**Implementation:**

1. Extract detailed error information from protobuf4j exceptions:
   ```java
   catch (RuntimeException e) {
       String detailedMessage = extractProtocError(e);
       throw new IOException("Failed to compile protobuf schema '" + fileName + "': " + detailedMessage, e);
   }

   private static String extractProtocError(RuntimeException e) {
       // Extract line numbers, column info, and specific error messages
       // from protobuf4j's exception structure
       Throwable cause = e.getCause();
       if (cause != null && cause.getMessage() != null) {
           return cause.getMessage();
       }
       return e.getMessage();
   }
   ```

2. Consider structured error objects for programmatic access

**Acceptance Criteria:**
- [x] Detailed error messages exposed to callers (via `extractProtocError()` method)
- [x] Line/column information preserved when available
- [ ] Error message format documented

---

## Phase 5: Batch Operations (Future)

### 5.1 Implement Batch Schema Compilation

**Priority:** Low | **Effort:** High | **Risk:** Medium

**Problem:**
Multi-file parsing with dependencies performs multiple passes, each potentially creating new WASM instances.

**Implementation:**

1. Add batch compilation API:
   ```java
   public class ProtobufBatchCompiler {

       public Map<String, FileDescriptor> compileAll(
               Map<String, String> schemas,
               Map<String, String> dependencies) {

           try (ProtobufCompilationContext ctx = ProtobufCompilationContext.acquire()) {
               // Write all files once
               // Single call to buildFileDescriptors with all files
               // Return map of filename -> FileDescriptor
           }
       }
   }
   ```

2. Use topological sorting for dependency order

3. Single WASM invocation for all schemas

**Use Cases:**
- Maven plugin processing multiple .proto files
- Bulk schema import
- Migration tools

**Acceptance Criteria:**
- [ ] Batch compilation API implemented
- [ ] Single WASM invocation for N schemas
- [ ] Proper dependency resolution
- [ ] Performance benchmark showing improvement over sequential compilation

---

## Implementation Schedule

| Phase | Task | Dependencies | Estimated Effort |
|-------|------|--------------|------------------|
| 1.1 | Centralize well-known types | None | 1 day |
| 1.2 | Clarify proto text methods | None | 0.5 days |
| 2.1 | FileSystem pooling | None | 2-3 days |
| 2.2 | WASM instance caching | 2.1 | 1-2 days |
| 2.3 | Well-known types caching | 2.1 | 1 day |
| 3.1 | Compatibility checker integration | None | 2 days |
| 3.2 | Dereferencer enhancement | 1.1 | 3 days |
| 4.1 | Remove unused code | None | 0.5 days |
| 4.2 | Enhance error messages | None | 0.5 days |
| 5.1 | Batch compilation | 2.1, 2.2 | 3-4 days |

**Recommended Order:**
1. Phase 1 (quick wins) - Immediate value, low risk
2. Phase 3.1 (compatibility checker) - High impact for validation workflows
3. Phase 2.1 + 2.2 (pooling) - Performance improvements
4. Phase 4 (cleanup) - Technical debt reduction
5. Phase 3.2 + 5.1 (advanced features) - Future enhancements

---

## Testing Strategy

### Unit Tests
- New utility classes: 100% coverage
- Modified methods: Ensure existing tests pass + new edge cases

### Integration Tests
- Run full integration test suite after each phase
- Add specific tests for pooling behavior under load

### Performance Tests
- Benchmark before/after for Phase 2 changes
- Use `integration-tests/protobuf-benchmark` as baseline
- Target metrics:
  - 2x throughput improvement for repeated compilations
  - 5-10x improvement for normalization operations
  - No regression in single-compilation latency

### Backward Compatibility Tests
- `ProtobufBackwardCompatibilityIT` must pass after all changes
- Verify content IDs remain stable

---

## Rollback Plan

Each phase is independent and can be reverted if issues arise:

1. **Phase 1:** Simple code refactoring, revert by restoring original duplicate code
2. **Phase 2:** Pooling can be disabled by returning to create-new-instance pattern
3. **Phase 3:** New overloaded methods don't affect existing API
4. **Phase 4:** Cleanup is optional, original code can be restored
5. **Phase 5:** New API, no impact on existing functionality

---

## Known Issues for Long-Running Servers

The current `ProtobufCompilationContext` pooling implementation has several issues that should be addressed before production use.

### Dual-Use Challenge: Server vs Serdes

The `protobuf-schema-utilities` module is used by two fundamentally different types of applications:

| Aspect | Registry Server | Serdes (Client Libraries) |
|--------|-----------------|---------------------------|
| **Runtime** | Long-running Quarkus app | Embedded in Kafka clients |
| **CDI Available** | Yes | No |
| **Shutdown Hooks** | Quarkus `ShutdownEvent` | None guaranteed |
| **Usage Pattern** | Sporadic (schema registration) | Hot path (every message) |
| **Pooling Benefit** | Low | **High** |
| **Lifecycle** | Managed by container | Varies (script to long-running) |

**The Dilemma:**
- **For Server:** Pooling adds complexity for little benefit; schema registration is infrequent
- **For Serdes:** Pooling is critical; WASM cold-start on every Kafka message would be unacceptable

This dual-use constraint affects how we can address the issues below.

---

### Issue Summary

| Issue | Severity | Impact |
|-------|----------|--------|
| No graceful shutdown | **High** | Resource leaks on app shutdown/hot reload |
| Pool never shrinks | Medium | Memory stays elevated after peak load |
| Static pool vs CDI lifecycle | Medium | Issues with Quarkus dev mode, no metrics visibility |
| WASM state accumulation | Low | Unknown - depends on protobuf4j internals |
| Thread-unsafe `warmPool()` | Low | Minor - only creates extra contexts |

---

### Issue 1: No Graceful Shutdown Hook

**Severity:** High

**Problem:**
The static `POOL` queue holds contexts indefinitely. When the Quarkus application shuts down (or during hot reload in dev mode), pooled WASM instances and virtual filesystems are not properly closed.

**Impact:**
- Resource leaks on application shutdown
- Potential issues with Quarkus dev mode hot reload
- File handles and memory may not be released

**Recommended Fix:**
Create a CDI bean to clean up the pool on shutdown:

```java
package io.apicurio.registry.utils.protobuf.schema;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ProtobufCompilationContextLifecycle {

    void onShutdown(@Observes ShutdownEvent ev) {
        ProtobufCompilationContext.clearPool();
    }
}
```

**Acceptance Criteria:**
- [ ] CDI shutdown hook created
- [ ] Pool properly cleared on application shutdown
- [ ] Verified with Quarkus dev mode hot reload

---

### Issue 2: Pool Never Shrinks (Memory Ratcheting)

**Severity:** Medium

**Problem:**
Once created, pooled contexts remain in memory forever, even during low-traffic periods. If the server experiences a burst of concurrent protobuf operations, the pool grows to accommodate them (up to `POOL_SIZE=4`), but never shrinks back.

**Impact:**
- Memory usage ratchets up during peak load and never decreases
- Each pooled context holds a ZeroFs filesystem + WASM instance (~TBD MB each)
- In memory-constrained environments, this wastes resources

**Recommended Fix:**
Add idle eviction with timestamps:

```java
private long lastUsed = System.currentTimeMillis();
private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

// In a background thread or during acquire():
private static void evictIdleContexts() {
    long now = System.currentTimeMillis();
    POOL.removeIf(ctx -> {
        if (now - ctx.lastUsed > IDLE_TIMEOUT_MS) {
            ctx.closed = true;
            ctx.protobuf.close();
            ctx.fs.close();
            return true;
        }
        return false;
    });
}
```

**Acceptance Criteria:**
- [ ] Idle timeout implemented
- [ ] Background eviction or lazy eviction on acquire
- [ ] Memory profiling shows pool shrinks after idle period

---

### Issue 3: Static Pool vs CDI Lifecycle

**Severity:** Medium

**Problem:**
Using static fields for pooling can be problematic in a CDI/Quarkus environment:
- In dev mode, classes may be reloaded but static state persists across reloads
- No integration with Quarkus lifecycle (`@PreDestroy`, `@PostConstruct`)
- Not visible to health checks, metrics, or monitoring systems
- Cannot be configured via Quarkus configuration

**Impact:**
- Difficult to monitor pool state in production
- No visibility into pool utilization for capacity planning
- Potential stale state issues in dev mode

**Recommended Fix:**
Consider converting to a CDI-managed singleton:

```java
@ApplicationScoped
public class ProtobufCompilationContextPool {

    @ConfigProperty(name = "apicurio.protobuf.pool.size", defaultValue = "4")
    int poolSize;

    @ConfigProperty(name = "apicurio.protobuf.pool.idle-timeout-ms", defaultValue = "300000")
    long idleTimeoutMs;

    private final BlockingQueue<ProtobufCompilationContext> pool;

    @PostConstruct
    void init() {
        this.pool = new ArrayBlockingQueue<>(poolSize);
    }

    @PreDestroy
    void cleanup() {
        clearPool();
    }

    // Expose metrics
    @Gauge(name = "protobuf_pool_size", description = "Current pool size")
    public int getCurrentPoolSize() {
        return pool.size();
    }
}
```

**Acceptance Criteria:**
- [ ] Pool converted to CDI-managed bean (optional - evaluate trade-offs)
- [ ] Pool size configurable via Quarkus config
- [ ] Metrics exposed for monitoring
- [ ] Health check integration (optional)

---

### Issue 4: WASM State Accumulation

**Severity:** Low

**Problem:**
The `Protobuf` WASM instance is reused across multiple compilations. If protobuf4j doesn't fully clear internal state between compilations, there could be subtle state accumulation bugs.

**Impact:**
- Unknown - depends on protobuf4j internals
- Potential for subtle bugs that only appear after many compilations
- Difficult to diagnose in production

**Recommended Fix:**
1. Verify with protobuf4j maintainers that WASM instance reuse is safe
2. Add integration tests that perform many sequential compilations and verify correctness
3. Consider periodic context recreation as a safety measure:

```java
private int useCount = 0;
private static final int MAX_USES_BEFORE_RECREATE = 1000;

public void close() {
    useCount++;
    if (useCount > MAX_USES_BEFORE_RECREATE) {
        // Force close instead of returning to pool
        closed = true;
        protobuf.close();
        fs.close();
        return;
    }
    // Normal pool return logic...
}
```

**Acceptance Criteria:**
- [ ] Confirmed with protobuf4j that instance reuse is safe
- [ ] Stress test with 10,000+ sequential compilations
- [ ] Optional: Max-use limit implemented

---

### Issue 5: Thread-Unsafe `warmPool()` Method

**Severity:** Low

**Problem:**
The `warmPool()` method is not thread-safe:

```java
while (POOL.size() < POOL_SIZE) {
    ProtobufCompilationContext ctx = createNew(true);
    if (!POOL.offer(ctx)) { ... }
}
```

If multiple threads call `warmPool()` concurrently, they could all see `POOL.size() < POOL_SIZE` and create contexts, potentially creating more than needed.

**Impact:**
- Minor - the `offer()` call will reject extras, which are then closed
- Wastes some CPU creating contexts that are immediately discarded
- Not a correctness issue, just inefficiency

**Recommended Fix:**
Add synchronization or use `AtomicBoolean` for warmup:

```java
private static final AtomicBoolean warming = new AtomicBoolean(false);

public static void warmPool() throws IOException {
    if (!warming.compareAndSet(false, true)) {
        return; // Another thread is already warming
    }
    try {
        while (POOL.size() < POOL_SIZE) {
            ProtobufCompilationContext ctx = createNew(true);
            if (!POOL.offer(ctx)) {
                ctx.close();
                break;
            }
        }
    } finally {
        warming.set(false);
    }
}
```

**Acceptance Criteria:**
- [ ] `warmPool()` is thread-safe
- [ ] Concurrent calls don't create excess contexts

---

### Recommended Action Plan

Given the dual-use challenge (Server vs Serdes), solutions must work without CDI dependencies.

**Priority 1 (Before Production):**
1. Add JVM shutdown hook (Issue 1) - Works in both CDI and non-CDI environments

**Priority 2 (Near-term):**
2. Add idle eviction (Issue 2) - Important for memory efficiency in long-running clients
3. Make `warmPool()` thread-safe (Issue 5) - Quick fix

**Priority 3 (Evaluate):**
4. Verify WASM state safety (Issue 4) - Coordinate with protobuf4j maintainers
5. Consider optional CDI integration for server-side (Issue 3) - As an add-on, not replacement

---

### Proposed Solutions for Dual-Use

#### Option A: JVM Shutdown Hook (Recommended for Initial Fix)

Works in all environments without CDI dependency:

```java
public class ProtobufCompilationContext implements AutoCloseable {

    private static volatile boolean shutdownHookRegistered = false;

    static {
        registerShutdownHook();
    }

    private static synchronized void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                clearPool();
            }, "protobuf-pool-cleanup"));
            shutdownHookRegistered = true;
        }
    }

    // ... rest of implementation
}
```

**Pros:**
- Works everywhere (server, serdes, standalone apps)
- No CDI dependency
- Simple to implement

**Cons:**
- Shutdown hook ordering is not guaranteed
- Doesn't help with Quarkus dev mode hot reload
- Can't be disabled/reconfigured at runtime

---

#### Option B: Make Pooling Configurable

Allow different behavior for different use cases:

```java
public class ProtobufCompilationContext implements AutoCloseable {

    public enum PoolingMode {
        ENABLED,      // Full pooling (default for serdes)
        DISABLED,     // No pooling, create-per-use (option for server)
        SINGLE        // Single cached instance (compromise)
    }

    private static volatile PoolingMode mode = PoolingMode.ENABLED;

    /**
     * Configure pooling mode. Should be called early in application lifecycle.
     *
     * <p>Recommended settings:</p>
     * <ul>
     *   <li>Serdes (Kafka clients): ENABLED (default) - maximizes throughput</li>
     *   <li>Registry Server: DISABLED or SINGLE - simpler lifecycle</li>
     * </ul>
     */
    public static void setPoolingMode(PoolingMode newMode) {
        if (mode != newMode) {
            clearPool(); // Clean up existing pool when changing modes
            mode = newMode;
        }
    }

    public static ProtobufCompilationContext acquire() throws IOException {
        switch (mode) {
            case DISABLED:
                return createNew(false); // Never pool
            case SINGLE:
                return acquireSingleton();
            case ENABLED:
            default:
                return acquireFromPool();
        }
    }
}
```

**Server-side configuration (in Quarkus startup):**
```java
@ApplicationScoped
public class ProtobufConfiguration {

    void onStart(@Observes StartupEvent ev) {
        // Server doesn't need pooling - schema registration is infrequent
        ProtobufCompilationContext.setPoolingMode(PoolingMode.DISABLED);
    }
}
```

**Serdes uses default (pooling enabled)** - no configuration needed.

---

#### Option C: Separate Pool Implementations

Create environment-specific wrappers:

```
protobuf-schema-utilities/
  └── ProtobufCompilationContext.java  (core, no pooling)

app/ (server)
  └── ServerProtobufContext.java       (CDI-managed, no pooling)

serdes/
  └── SerdesProtobufContext.java       (static pool, JVM shutdown hook)
```

**Pros:**
- Clean separation of concerns
- Each environment gets optimal implementation

**Cons:**
- Code duplication
- More maintenance burden
- Harder to keep implementations in sync

---

#### Option D: Hybrid Approach (Recommended)

Combine Options A and B:

1. **JVM Shutdown Hook** - Always registered for basic cleanup
2. **Configurable Pooling Mode** - Allow environments to choose behavior
3. **Optional CDI Integration** - Server can add Quarkus-specific cleanup

```java
public class ProtobufCompilationContext implements AutoCloseable {

    // Configuration
    private static volatile boolean poolingEnabled = true;
    private static volatile int maxPoolSize = 4;
    private static volatile long idleTimeoutMs = 5 * 60 * 1000; // 5 minutes

    // JVM shutdown hook (always active)
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            clearPool();
        }, "protobuf-pool-cleanup"));
    }

    /**
     * Disable pooling entirely. Recommended for server-side where
     * schema operations are infrequent.
     */
    public static void disablePooling() {
        poolingEnabled = false;
        clearPool();
    }

    /**
     * Configure pool size. Only affects future context creation.
     */
    public static void setMaxPoolSize(int size) {
        maxPoolSize = size;
    }

    /**
     * Configure idle timeout for pool eviction.
     */
    public static void setIdleTimeoutMs(long timeoutMs) {
        idleTimeoutMs = timeoutMs;
    }

    public static ProtobufCompilationContext acquire() throws IOException {
        if (!poolingEnabled) {
            return createNew(false);
        }
        // Pool logic with idle eviction...
    }
}
```

**Registry Server adds optional CDI cleanup:**
```java
@ApplicationScoped
public class ProtobufLifecycle {

    void onStart(@Observes StartupEvent ev) {
        // Option 1: Disable pooling (simpler)
        ProtobufCompilationContext.disablePooling();

        // Option 2: Keep pooling but with shorter timeout
        // ProtobufCompilationContext.setIdleTimeoutMs(60_000); // 1 minute
    }

    void onShutdown(@Observes ShutdownEvent ev) {
        // Explicit cleanup (in addition to JVM hook, for dev mode)
        ProtobufCompilationContext.clearPool();
    }
}
```

---

### Recommendation Summary

| Environment | Recommended Configuration |
|-------------|---------------------------|
| **Registry Server** | Disable pooling OR short idle timeout (1 min) |
| **Kafka Serdes** | Keep pooling enabled (default) |
| **Short-lived scripts** | Pooling enabled, JVM hook handles cleanup |
| **Unit tests** | Call `clearPool()` in `@AfterEach` |

**Implementation Order:**
1. Add JVM shutdown hook (immediate - addresses Issue 1)
2. Add `disablePooling()` API (allows server to opt out)
3. Add idle eviction (addresses Issue 2)
4. Make `warmPool()` thread-safe (addresses Issue 5)
5. Add optional Quarkus CDI integration in `app/` module

---

## Success Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| Code duplication (well-known types) | 6 locations | 1 location | Code review |
| Compilation throughput | Baseline | 2x | Benchmark |
| Normalization throughput | Baseline | 5-10x | Benchmark |
| Memory per compilation | TBD | -30% | Profiling |
| Test coverage (protobuf utils) | TBD | >80% | JaCoCo |

---

## Appendix: File Reference

### Files Created ✅
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufWellKnownTypes.java` ✅
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufCompilationContext.java` ✅
- `utils/protobuf-schema-utilities/src/test/java/io/apicurio/registry/utils/protobuf/schema/ProtobufWellKnownTypesTest.java` ✅
- `utils/protobuf-schema-utilities/src/test/java/io/apicurio/registry/utils/protobuf/schema/ProtobufCompilationContextTest.java` (not yet created - optional)

### Files Modified ✅
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaLoader.java` ✅
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchemaUtils.java` ✅
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufSchema.java` ✅
- `utils/protobuf-schema-utilities/src/main/java/io/apicurio/registry/utils/protobuf/schema/ProtobufFile.java` ✅
- `schema-util/protobuf/src/main/java/io/apicurio/registry/protobuf/content/canon/ProtobufContentCanonicalizer.java` ✅
- `schema-util/protobuf/src/main/java/io/apicurio/registry/protobuf/content/dereference/ProtobufDereferencer.java` ✅
- `schema-util/protobuf/src/main/java/io/apicurio/registry/protobuf/rules/compatibility/ProtobufCompatibilityChecker.java` ✅
- `serdes/generic/serde-common-protobuf/src/main/java/io/apicurio/registry/serde/protobuf/ProtobufSchemaParser.java` ✅
