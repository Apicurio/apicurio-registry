# Downstream Migration Plan: Remove wire-schema Dependencies

**Date:** 2025-11-18
**Status:** 📋 **PLAN - Ready for Implementation**

## Executive Summary

Several downstream modules still depend on wire-schema library for protobuf AST functionality. This document provides a comprehensive migration plan to eliminate these dependencies by leveraging the new protobuf4j `buildFileDescriptors()` approach.

## Affected Modules

### 1. **serdes/generic/serde-common-protobuf** ⚠️ **HIGH IMPACT**
- **File:** `ProtobufSchemaParser.java`
- **Dependencies:** `wire-schema`, `wire-schema-jvm`
- **Wire Usage:**
  - `ProtoParser` - Parse .proto text to AST
  - `ProtoFileElement` - Store AST representation
  - `MessageElement` - Access message definitions
- **FileDescriptorUtils Methods:**
  - `firstMessage(ProtoFileElement)` ❌ Removed
  - `toDescriptor(String, ProtoFileElement, Map)` ❌ Removed
  - `fileDescriptorToProtoFile(FileDescriptorProto)` ❌ Removed
  - `protoFileToFileDescriptor(ProtoFileElement)` ❌ Removed

### 2. **schema-util/protobuf** ⚠️ **HIGH IMPACT**
Multiple files using wire-schema:

**a) ProtobufContentCanonicalizer.java**
- Uses `ProtoParser` to parse and canonicalize proto schemas
- Uses `ProtoFileElement.toSchema()` to convert back to text

**b) ProtobufContentValidator.java**
- Uses `firstMessage(ProtoFileElement)` ❌ Removed
- Validates protobuf content

**c) ProtobufDereferencer.java**
- Uses `firstMessage(ProtoFileElement)` ❌ Removed
- Dereferences protobuf schemas

**d) ProtobufReferenceFinder.java**
- Finds references in protobuf schemas

**e) ProtobufCompatibilityCheckerLibrary.java**
- Checks compatibility between protobuf schemas

### 3. **app/src/main/java** 🔸 **MEDIUM IMPACT**

**a) AbstractResource.java**
- Uses `ProtoParser` and `ProtoFileElement`
- Uses `fileDescriptorToProtoFile()` ❌ Removed
- Part of Confluent Schema Registry compatibility API

**b) SchemaFormatService.java**
- Similar usage pattern

### 4. **integration-tests** 🟢 **LOW IMPACT**
- Test files using wire-schema generated classes
- Can be updated after main modules

## Root Cause Analysis

### Why These Components Use wire-schema

1. **AST Access** - Need to inspect proto file structure (messages, fields, options)
2. **Schema Manipulation** - Need to modify proto schemas programmatically
3. **Canonicalization** - Need to convert schemas to canonical text format
4. **Compatibility Checking** - Need to compare schema structures
5. **ProtobufSchema Storage** - `ProtobufSchema` class stores `ProtoFileElement`

### Why Our Migration Removed These Methods

The methods were removed because they required wire-schema AST types:
```java
// These return or accept wire-schema types
MessageElement firstMessage(ProtoFileElement)
Descriptor toDescriptor(String, ProtoFileElement, Map)
ProtoFileElement fileDescriptorToProtoFile(FileDescriptorProto)
FileDescriptor protoFileToFileDescriptor(ProtoFileElement)
```

## Migration Strategy

### Approach 1: Use FileDescriptor API (Recommended)

**Concept:** Work directly with Google's `FileDescriptor` and `DescriptorProto` APIs instead of wire-schema AST.

**Benefits:**
✅ Pure protobuf solution - no wire-schema needed
✅ Well-documented Google Protobuf API
✅ Future-proof
✅ Performance - no conversions needed

**Drawbacks:**
❌ Different API than wire-schema
❌ Requires code changes in downstream modules
❌ Less "source-like" representation

**Implementation:**
```java
// Before (wire-schema)
ProtoFileElement element = ProtoParser.parse(location, content);
MessageElement message = element.getTypes().get(0);
String messageName = message.getName();

// After (Google Protobuf API)
FileDescriptor descriptor = Protobuf.buildFileDescriptors(tempDir, files).get(0);
Descriptor message = descriptor.getMessageTypes().get(0);
String messageName = message.getName();
```

### Approach 2: Add Text Format Converter (Alternative)

**Concept:** Implement `FileDescriptor` → text converter to replace `fileDescriptorToProtoFile()`.

**Benefits:**
✅ Minimal code changes in downstream modules
✅ Can generate .proto text from FileDescriptor
✅ No wire-schema dependency

**Drawbacks:**
❌ Need to implement text format generator
❌ Complex to handle all proto features correctly
❌ Maintenance burden

**Implementation:**
```java
public class FileDescriptorToTextConverter {
    public static String toProtoText(FileDescriptor descriptor) {
        // Implement text generation from FileDescriptor
        // Handle: syntax, package, imports, messages, enums, services, options
    }
}
```

### Approach 3: Refactor ProtobufSchema Class (Comprehensive)

**Concept:** Change `ProtobufSchema` to store only `FileDescriptor`, not `ProtoFileElement`.

**Benefits:**
✅ Cleanest solution - no AST at all
✅ Aligns with protobuf4j approach
✅ Eliminates wire-schema completely

**Drawbacks:**
❌ Breaking change to ProtobufSchema API
❌ Requires updates in ALL modules using ProtobufSchema
❌ Most work required

**Implementation:**
```java
// Before
public class ProtobufSchema {
    private final FileDescriptor fileDescriptor;
    private final ProtoFileElement protoFileElement;  // ❌ wire-schema type

    public ProtoFileElement getProtoFileElement() { ... }
}

// After
public class ProtobufSchema {
    private final FileDescriptor fileDescriptor;
    // ProtoFileElement removed

    // Add new methods working with FileDescriptor
    public String getFirstMessageName() {
        return fileDescriptor.getMessageTypes().isEmpty()
            ? null
            : fileDescriptor.getMessageTypes().get(0).getName();
    }
}
```

## Recommended Migration Path

### Phase 1: Update ProtobufSchema Class (FOUNDATION) 🏗️

**Goal:** Make ProtobufSchema work without wire-schema AST

**Tasks:**
1. Add helper methods to ProtobufSchema for common operations:
   ```java
   // New methods using FileDescriptor API
   String getFirstMessageName()
   List<String> getMessageNames()
   Descriptor findMessage(String name)
   String toProtoText()  // Optional: if text format needed
   ```

2. Deprecate `getProtoFileElement()` but keep for backward compatibility
3. Update constructors to make `ProtoFileElement` optional

**Affected Classes:**
- `utils/protobuf-schema-utilities/ProtobufSchema.java`

**Migration Example:**
```java
// Before
ProtoFileElement element = schema.getProtoFileElement();
MessageElement message = element.getTypes().get(0);

// After
Descriptor message = schema.getFileDescriptor().getMessageTypes().get(0);
// OR
String messageName = schema.getFirstMessageName();
```

### Phase 2: Update serdes/serde-common-protobuf (CRITICAL PATH) 🔥

**Goal:** Remove wire-schema from serializer/deserializer

**File:** `ProtobufSchemaParser.java`

**Current Wire Usage:**
```java
// Parse proto text
ProtoFileElement fileElem = ProtoParser.parse(location, content);

// Get first message
MessageElement firstMessage = FileDescriptorUtils.firstMessage(fileElem);

// Convert to descriptor
Descriptor descriptor = FileDescriptorUtils.toDescriptor(messageName, fileElem, deps);

// Store in ProtobufSchema
return new ProtobufSchema(fileDescriptor, fileElem);
```

**New Approach:**
```java
// Parse and compile in one step using protobuf4j
List<FileDescriptor> descriptors = ProtobufSchemaUtils.parseAndCompile(content, dependencies);
FileDescriptor fileDescriptor = descriptors.get(0);

// Store in ProtobufSchema (without ProtoFileElement)
return new ProtobufSchema(fileDescriptor);
```

**New Helper Class:**
```java
public class ProtobufSchemaUtils {
    public static List<FileDescriptor> parseAndCompile(
            String protoContent,
            Map<String, String> dependencies) throws IOException {

        Path tempDir = Files.createTempDirectory("protobuf-parse");
        try {
            // Write proto files to temp directory
            writeProtoFile(tempDir, "main.proto", protoContent);
            for (Map.Entry<String, String> dep : dependencies.entrySet()) {
                writeProtoFile(tempDir, dep.getKey(), dep.getValue());
            }

            // Use protobuf4j to compile
            return Protobuf.buildFileDescriptors(tempDir, List.of("main.proto"));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public static String getFirstMessageName(FileDescriptor descriptor) {
        return descriptor.getMessageTypes().isEmpty()
            ? null
            : descriptor.getMessageTypes().get(0).getName();
    }
}
```

**Tasks:**
1. Create `ProtobufSchemaUtils` helper class
2. Update `parseSchema()` method to use protobuf4j
3. Remove all wire-schema imports
4. Update tests

**Impact:** ⚠️ HIGH - This is the serializer used by all Kafka producers/consumers

### Phase 3: Update schema-util/protobuf (VALIDATION & RULES) 🔍

**Goal:** Update validators, canonicalizers, and compatibility checkers

#### 3a. ProtobufContentCanonicalizer.java

**Current:**
```java
ProtoFileElement fileElem = ProtoParser.parse(location, content);
return ContentHandle.create(fileElem.toSchema());
```

**Option A - Use FileDescriptor.toProto().toString():**
```java
List<FileDescriptor> fds = ProtobufSchemaUtils.parseAndCompile(content, refs);
return ContentHandle.create(fds.get(0).toProto().toString());
```

**Option B - Keep text parsing, use protobuf4j for validation:**
```java
// Parse to validate, but return original content if valid
ProtobufSchemaUtils.parseAndCompile(content, refs);  // Validates
return content;  // Return original if valid
```

#### 3b. ProtobufContentValidator.java

**Current:**
```java
ProtoFileElement fileElem = ProtoParser.parse(location, content);
MessageElement firstMessage = FileDescriptorUtils.firstMessage(fileElem);
String messageName = firstMessage.getName();
```

**New:**
```java
List<FileDescriptor> fds = ProtobufSchemaUtils.parseAndCompile(content, deps);
String messageName = ProtobufSchemaUtils.getFirstMessageName(fds.get(0));
```

#### 3c. ProtobufDereferencer.java

**Similar pattern** - replace `firstMessage()` with FileDescriptor API

#### 3d. ProtobufReferenceFinder.java

**Current:** Parses proto text to find import statements

**New:** Can use protobuf4j compilation - it will identify all dependencies

```java
List<FileDescriptor> fds = ProtobufSchemaUtils.parseAndCompile(content, Collections.emptyMap());
List<String> references = fds.get(0).getDependencies()
    .stream()
    .map(FileDescriptor::getName)
    .collect(Collectors.toList());
```

#### 3e. ProtobufCompatibilityCheckerLibrary.java

**Goal:** Check compatibility without AST

**Current Approach:**
- Compares `ProtoFileElement` structures

**New Approach:**
```java
// Compare FileDescriptors
boolean compatible = checkCompatibility(
    oldDescriptor.toProto(),
    newDescriptor.toProto()
);
```

Use `DescriptorProto` for structural comparison instead of AST.

### Phase 4: Update app/ REST API (COMPATIBILITY LAYER) 🌐

**Files:**
- `AbstractResource.java`
- `SchemaFormatService.java`

**Current Usage:**
```java
ProtoFileElement element = ProtoParser.parse(location, content);
// ... process element
```

**New Approach:**
```java
List<FileDescriptor> fds = ProtobufSchemaUtils.parseAndCompile(content, deps);
FileDescriptor fd = fds.get(0);
// ... process descriptor
```

**Special Consideration:**
These files are part of Confluent Schema Registry compatibility API. Changes should not affect external API contracts.

### Phase 5: Update integration-tests (TESTING) ✅

**Goal:** Update test utilities and factories

**Files:**
- `ProtobufTestMessageFactory.java`
- `ProtobufUUIDTestMessage.java`

**Approach:**
- Update to use protobuf4j compiled descriptors
- May need to pre-compile test proto files

### Phase 6: Remove wire-schema Dependencies (CLEANUP) 🧹

**Files to Update:**
1. Root `pom.xml` - Remove wire-schema from dependency management
2. `serdes/generic/serde-common-protobuf/pom.xml` - Remove dependencies

**Verification:**
```bash
# Ensure no wire-schema imports remain
grep -r "import com.squareup.wire" --include="*.java" --exclude-dir=target

# Ensure no wire-schema dependencies
find . -name "pom.xml" -exec grep -l "wire-schema" {} \;
```

## Implementation Timeline

### Week 1: Foundation
- [ ] Phase 1: Update ProtobufSchema class
- [ ] Create ProtobufSchemaUtils helper class
- [ ] Write comprehensive unit tests

### Week 2: Critical Path
- [ ] Phase 2: Update ProtobufSchemaParser
- [ ] Update serializer/deserializer tests
- [ ] Integration testing with Kafka

### Week 3: Utilities
- [ ] Phase 3a-c: Update validators and canonicalizers
- [ ] Phase 3d-e: Update reference finder and compatibility checker
- [ ] Unit tests for all changes

### Week 4: REST API & Integration
- [ ] Phase 4: Update app/ REST APIs
- [ ] Phase 5: Update integration tests
- [ ] End-to-end testing

### Week 5: Cleanup & Testing
- [ ] Phase 6: Remove all wire-schema dependencies
- [ ] Full regression testing
- [ ] Performance benchmarking

## Migration Helpers

### Helper Class: ProtobufSchemaUtils.java

Create in `utils/protobuf-schema-utilities/src/main/java/.../`:

```java
package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.Descriptors;
import io.roastedroot.protobuf4j.Protobuf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility class for protobuf schema operations.
 * Replaces wire-schema functionality with protobuf4j.
 */
public class ProtobufSchemaUtils {

    /**
     * Parse and compile a protobuf schema with dependencies.
     * Replaces: ProtoParser.parse() + FileDescriptorUtils.toDescriptor()
     */
    public static List<Descriptors.FileDescriptor> parseAndCompile(
            String protoContent,
            Map<String, String> dependencies) throws IOException {

        Path tempDir = Files.createTempDirectory("protobuf-parse");
        try {
            // Write main proto file
            ProtoContent main = new ProtoContent("main.proto", protoContent);
            main.writeTo(tempDir);

            // Write dependencies
            for (Map.Entry<String, String> dep : dependencies.entrySet()) {
                ProtoContent depContent = new ProtoContent(dep.getKey(), dep.getValue());
                depContent.writeTo(tempDir);
            }

            // Compile using protobuf4j
            return Protobuf.buildFileDescriptors(tempDir,
                List.of(main.getExpectedImportPath()));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * Get the name of the first message in a FileDescriptor.
     * Replaces: FileDescriptorUtils.firstMessage(ProtoFileElement).getName()
     */
    public static String getFirstMessageName(Descriptors.FileDescriptor descriptor) {
        return descriptor.getMessageTypes().isEmpty()
            ? null
            : descriptor.getMessageTypes().get(0).getName();
    }

    /**
     * Get the first message Descriptor from a FileDescriptor.
     * Replaces: FileDescriptorUtils.firstMessage(ProtoFileElement)
     */
    public static Descriptors.Descriptor getFirstMessage(Descriptors.FileDescriptor descriptor) {
        return descriptor.getMessageTypes().isEmpty()
            ? null
            : descriptor.getMessageTypes().get(0);
    }

    /**
     * Find a message by name in a FileDescriptor.
     */
    public static Descriptors.Descriptor findMessage(
            Descriptors.FileDescriptor descriptor, String messageName) {
        return descriptor.findMessageTypeByName(messageName);
    }

    /**
     * Convert FileDescriptor to proto text format (optional - if needed).
     * This would need to be implemented if text format output is required.
     */
    public static String toProtoText(Descriptors.FileDescriptor descriptor) {
        // Option 1: Use FileDescriptorProto.toString() (not perfect but functional)
        return descriptor.toProto().toString();

        // Option 2: Implement proper text format generator (more work)
        // return new ProtoTextGenerator().generate(descriptor);
    }

    private static void deleteRecursively(Path path) throws IOException {
        // ... implementation
    }
}
```

## Testing Strategy

### Unit Tests
- Test each updated module independently
- Verify protobuf parsing works correctly
- Ensure backward compatibility where applicable

### Integration Tests
- Test serializer/deserializer with Kafka
- Test schema registry compatibility API
- Test validation and compatibility checking

### Performance Tests
- Benchmark schema parsing speed
- Compare memory usage
- Verify no regressions

## Risk Mitigation

### Risk 1: Breaking Changes
**Risk:** API changes break downstream applications

**Mitigation:**
- Maintain deprecated methods temporarily
- Provide compatibility layer
- Document migration path clearly
- Gradual rollout

### Risk 2: Functional Differences
**Risk:** protobuf4j behaves differently than wire-schema

**Mitigation:**
- Comprehensive testing
- Compare outputs between old/new implementations
- Use protobuf4j's proven compilation path

### Risk 3: Performance Impact
**Risk:** New approach is slower

**Mitigation:**
- Benchmark before/after
- Profile hot paths
- Optimize if needed
- Cache compiled descriptors

## Success Criteria

- [ ] Zero wire-schema dependencies in codebase
- [ ] All tests passing
- [ ] No performance regression (< 5% slower)
- [ ] Backward compatible APIs where possible
- [ ] Complete documentation of changes

## Rollback Plan

If migration encounters blocking issues:

1. **Temporary Compatibility Layer**
   - Keep wire-schema for specific modules
   - Wrap wire-schema usage in abstraction layer
   - Migrate gradually

2. **Hybrid Approach**
   - New code uses protobuf4j
   - Legacy code keeps wire-schema
   - Plan long-term migration

3. **Defer Migration**
   - Keep wire-schema until better solution available
   - Document blockers
   - Reassess quarterly

## Conclusion

This migration will eliminate wire-schema dependencies across the entire Apicurio Registry codebase, replacing them with the native protobuf4j `buildFileDescriptors()` approach. While it requires updates across multiple modules, the benefits of a pure JVM solution without platform-specific dependencies make it worthwhile.

**Estimated Effort:** 3-5 weeks (1 developer)
**Complexity:** Medium-High
**Impact:** High - touches serializers, validators, and REST APIs
**Recommended Approach:** Approach 1 (Use FileDescriptor API) + Create ProtobufSchemaUtils helper

---

**Next Steps:**
1. Review and approve this migration plan
2. Create JIRA tickets for each phase
3. Begin with Phase 1 (Foundation)
4. Proceed incrementally with continuous testing
