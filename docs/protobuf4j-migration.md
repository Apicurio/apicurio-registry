# Protobuf4j Migration: From Wire-Schema to Native Protoc

## Executive Summary

| Metric | Before (Wire-Schema) | After (protobuf4j) | Change |
|--------|---------------------|-------------------|--------|
| Direct dependencies | 7 | 1 | -86% |
| Transitive JARs | ~20 | ~8 | -60% |
| Total dependency size | ~19 MB | ~4 MB | **-79%** |
| Code lines (FileDescriptorUtils.java) | 1,605 | 432 | -73% |
| Kotlin runtime required | Yes | No | Removed |
| protoc compatibility | Approximate | 100% exact | Improved |

## Overview

Apicurio Registry has migrated from using **Square's Wire-Schema** library to **protobuf4j** for protobuf schema handling. This migration provides significant benefits in terms of dependency reduction, consistency with the official protobuf compiler, and improved canonicalization capabilities.

## Motivation

### Problems with Wire-Schema

Wire-Schema (`com.squareup.wire:wire-schema`) is a Kotlin-based library that re-implements protobuf parsing and schema handling. While functional, it presented several challenges:

1. **Heavy Dependency Tree**: Wire-Schema brought in a substantial dependency chain:
   - `wire-schema` and `wire-schema-jvm` (~500 KB)
   - `okio` and `okio-jvm` (~376 KB)
   - `okio-fakefilesystem` (for virtual filesystem operations)
   - `icu4j` (International Components for Unicode, **~14 MB**)
   - `kotlin-stdlib` (~1.6 MB)
   - `okhttp` (HTTP client, often unused)

2. **Semantic Inconsistencies**: Wire-Schema's interpretation of protobuf semantics occasionally differed from the official `protoc` compiler, leading to edge cases where schemas valid in Wire weren't valid in protoc and vice versa.

3. **Canonicalization Limitations**: Wire's text-to-text canonicalization couldn't guarantee exact semantic equivalence because it didn't use `protoc`'s internal normalization.

4. **Maintenance Overhead**: Keeping Wire-Schema aligned with the latest protobuf specification required ongoing effort.

### Why protobuf4j?

**protobuf4j** (`io.roastedroot:protobuf4j-v4`) takes a fundamentally different approach: it compiles the official `protoc` compiler to **WebAssembly (WASM)** and runs it on the JVM using [Chicory](https://github.com/nickel-lang/chicory), a pure Java WASM runtime.

Key advantages:

1. **100% protoc Compatibility**: By running the actual `protoc` binary (compiled to WASM), protobuf4j guarantees identical behavior to the native compiler.

2. **Official Normalization**: protobuf4j provides `normalizeSchemaToText()` which produces canonicalized proto text using protoc's internal normalization algorithms.

3. **Syntax Validation**: The `validateSyntax()` method provides accurate syntax checking without needing to resolve imports.

4. **Reduced Dependencies**: The entire protobuf4j stack is lighter than Wire's Kotlin ecosystem.

## Architecture

### How protobuf4j Works

```
┌─────────────────────────────────────────────────────────────────┐
│                        protobuf4j                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐    ┌──────────────┐    ┌───────────────────┐   │
│  │ Proto Text  │───>│   ZeroFs     │───>│  protoc (WASM)    │   │
│  │   Input     │    │  Virtual FS  │    │  via Chicory      │   │
│  └─────────────┘    └──────────────┘    └─────────┬─────────┘   │
│                                                    │             │
│                          ┌────────────────────────┘             │
│                          ▼                                       │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              FileDescriptor / FileDescriptorProto        │    │
│  │            (Google Protobuf Java API)                    │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### Key Components

1. **ZeroFs** (`io.roastedroot:zerofs`): A virtual filesystem implementation that maps to WASI (WebAssembly System Interface). Since WASM modules interact with the filesystem through WASI, ZeroFs provides an in-memory filesystem that protoc can read from and write to.

2. **Chicory**: A pure Java WASM runtime that executes the compiled `protoc` binary. This eliminates the need for native binaries or JNI.

3. **protoc WASM**: The official Google protobuf compiler compiled to WebAssembly, ensuring 100% compatibility with native protoc behavior.

### Core APIs

```java
// Main compilation entry point
Protobuf.buildFileDescriptors(Path workDir, List<String> protoFiles)
    -> List<FileDescriptor>

// Schema normalization (canonicalization)
Protobuf.normalizeSchemaToText(FileDescriptorSet fds)
    -> Map<String, String>  // filename -> normalized proto text

// Syntax validation (without import resolution)
Protobuf.validateSyntax(Path workDir, String protoFile)
    -> ValidationResult
```

## Implementation Details

### ProtobufSchemaLoader

The central class for protobuf compilation in Apicurio Registry:

```java
// utils/protobuf-schema-utilities/src/main/java/.../ProtobufSchemaLoader.java

public static ProtobufSchemaLoaderContext loadSchema(
    String fileName,
    String schemaDefinition,
    Map<String, String> deps) throws IOException {

    // 1. Create virtual filesystem for WASM protoc
    FileSystem fs = ZeroFs.newFileSystem(
        Configuration.unix().toBuilder()
            .setAttributeViews("unix")
            .build());

    // 2. Write well-known protos (google.protobuf.*, google.type.*)
    writeWellKnownProtos(workDir);

    // 3. Write main proto and dependencies to virtual FS
    for (ProtoContent proto : allDependencies) {
        proto.writeTo(workDir);
    }

    // 4. Compile using protobuf4j
    List<FileDescriptor> fileDescriptors =
        Protobuf.buildFileDescriptors(workDir, protoFiles);

    // 5. Return the main FileDescriptor
    return new ProtobufSchemaLoaderContext(mainDescriptor);
}
```

### Canonicalization

The `ProtobufContentCanonicalizer` now uses protobuf4j's normalization:

```java
// schema-util/protobuf/src/main/java/.../ProtobufContentCanonicalizer.java

@Override
public TypedContent canonicalize(TypedContent content,
                                  Map<String, TypedContent> resolvedReferences) {
    // 1. Compile schema to FileDescriptor
    FileDescriptor fileDescriptor = ProtobufSchemaUtils.parseAndCompile(
        "schema.proto", schemaContent, dependencies);

    // 2. Build FileDescriptorSet with all dependencies
    FileDescriptorSet.Builder fdsBuilder = FileDescriptorSet.newBuilder();
    addFileDescriptorToSet(fileDescriptor, fdsBuilder, addedFiles);

    // 3. Use protobuf4j's normalization
    Map<String, String> normalizedSchemas =
        Protobuf.normalizeSchemaToText(fdsBuilder.build());

    // 4. Return canonical form
    return TypedContent.create(
        ContentHandle.create(normalizedSchemas.get("schema.proto")),
        ContentTypes.APPLICATION_PROTOBUF);
}
```

### Syntax Validation

Fast syntax validation without dependency resolution:

```java
// utils/protobuf-schema-utilities/src/main/java/.../ProtobufFile.java

public static void validateSyntaxOnly(String data) throws IOException {
    FileSystem fs = ZeroFs.newFileSystem(
        Configuration.unix().toBuilder()
            .setAttributeViews("unix")
            .build());

    try (FileSystem ignored = fs) {
        Path workDir = fs.getPath(".");
        Files.write(workDir.resolve("schema.proto"),
                    data.getBytes(StandardCharsets.UTF_8));

        // Use protobuf4j's validateSyntax
        ValidationResult result = Protobuf.validateSyntax(workDir, "schema.proto");
        if (!result.isValid()) {
            throw new IOException("Invalid protobuf syntax: " + result.getErrors());
        }
    }
}
```

## Dependency Comparison

### Before (Wire-Schema)

```xml
<dependencies>
  <!-- Wire Schema -->
  <dependency>com.squareup.wire:wire-schema</dependency>      <!-- 36 KB -->
  <dependency>com.squareup.wire:wire-schema-jvm</dependency>  <!-- 500 KB -->

  <!-- Okio ecosystem -->
  <dependency>com.squareup.okio:okio-jvm</dependency>         <!-- 376 KB -->
  <dependency>com.squareup.okio:okio-fakefilesystem</dependency>

  <!-- Kotlin runtime -->
  <dependency>org.jetbrains.kotlin:kotlin-stdlib</dependency> <!-- 1.6 MB -->

  <!-- ICU4J (heavy!) -->
  <dependency>com.ibm.icu:icu4j</dependency>                  <!-- 14 MB -->
</dependencies>
```

**Measured JAR sizes (from Maven repository):**

| Dependency | Size |
|------------|------|
| wire-schema-jvm-5.3.3.jar | 488 KB |
| okio-jvm-3.16.4.jar | 376 KB |
| kotlin-stdlib-1.9.25.jar | 1.6 MB |
| icu4j-76.1.jar | **14 MB** |
| **Total (key dependencies)** | **~19 MB** |

### After (protobuf4j)

```xml
<dependencies>
  <!-- protobuf4j (includes WASM runtime) -->
  <dependency>io.roastedroot:protobuf4j-v4</dependency>
</dependencies>
```

**Transitive dependencies:**

| Dependency | Size |
|------------|------|
| protobuf4j-v4.jar | 1.3 MB |
| zerofs.jar | ~100 KB |
| chicory-wasi.jar | ~200 KB |
| chicory-runtime.jar | ~1.5 MB |
| chicory-wasm.jar | ~500 KB |
| chicory-log.jar | ~50 KB |
| **Total** | **~4 MB** |

### Dependency Reduction Summary

| Aspect | Wire-Schema | protobuf4j | Change |
|--------|-------------|------------|--------|
| Direct dependencies | 7 | 1 | -86% |
| Transitive JARs | ~20 | ~8 | -60% |
| Total size | ~19 MB | ~4 MB | **-79%** |
| Kotlin required | Yes | No | Removed |
| ICU4J required | Yes | No | Removed |
| JNI/Native code | No | No | Same |

## Protobuf Serializer Size Comparison

The Apicurio protobuf serializer module and its dependencies:

### Serializer JAR Sizes

| Module | JAR Size |
|--------|----------|
| apicurio-registry-serde-common-protobuf | 36 KB |
| apicurio-registry-protobuf-serde-kafka | 24 KB |
| apicurio-registry-serde-common | 32 KB |
| apicurio-registry-serde-kafka-common | 20 KB |
| **Total Apicurio serdes** | **~112 KB** |

### Dependency Comparison for Serializers

When including transitive dependencies needed for protobuf serialization:

| Component | Wire-Schema Stack | protobuf4j Stack |
|-----------|------------------|------------------|
| Core protobuf parsing | wire-schema-jvm (500 KB) | protobuf4j-v4 (1.3 MB) |
| I/O library | okio-jvm (376 KB) | zerofs (~100 KB) |
| Runtime | kotlin-stdlib (1.6 MB) | chicory-runtime (1.5 MB) |
| Unicode support | icu4j (**14 MB**) | Not needed |
| **Total additional deps** | **~16.5 MB** | **~3 MB** |

**Key insight**: The wire-schema stack required **icu4j** (~14 MB) for Unicode normalization, which is the single largest dependency. protobuf4j eliminates this requirement entirely.

## Performance Characteristics

### Compilation Performance

protobuf4j uses WASM, which has inherent overhead compared to native code. However:

- **Cold start**: First compilation incurs WASM module instantiation (~100-200ms)
- **Warm performance**: Subsequent compilations are faster due to JIT optimization
- **Caching**: Well-known types are cached to avoid repeated filesystem writes

### Serialization/Deserialization Performance

The migration from wire-schema to protobuf4j has **minimal impact** on serialization/deserialization performance because:

1. **Schema parsing happens once**: The FileDescriptor is parsed when the serializer is initialized
2. **Runtime uses Google Protobuf**: Actual message serialization uses `com.google.protobuf` directly
3. **No WASM at runtime**: protobuf4j/WASM is only used for schema compilation, not message handling

A performance comparison test (`SerdesPerformanceIT`) compares:
- **OLD**: Apicurio wire-schema based serializer (v3.1.2)
- **NEW**: Apicurio protobuf4j based serializer (current version)
- **CONFLUENT**: Confluent's KafkaProtobufSerializer

The test measures:
- Simple message serialization (UUID with 2 fields)
- Complex message serialization (nested protobuf)
- Round-trip (serialize + deserialize)

**Expected results**: The new protobuf4j-based serializer should perform comparably to the old wire-schema version, as the serialization hot path is identical (both use Google's protobuf-java library).

### Canonicalization Consistency

The key benefit of protobuf4j is **consistency with protoc**:

```
Input Schema A          Input Schema B
      │                       │
      ▼                       ▼
┌─────────────────────────────────────┐
│     protobuf4j normalization        │
│     (runs actual protoc WASM)       │
└─────────────────────────────────────┘
      │                       │
      ▼                       ▼
  Canonical A             Canonical B
      │                       │
      └───────────────────────┘
                │
                ▼
        Byte-for-byte comparison
        (semantically equivalent schemas
         produce identical output)
```

This guarantees that two semantically equivalent schemas will produce identical canonical forms, which is critical for:
- Content-based deduplication
- Schema compatibility checking
- Content ID generation

## Migration Impact

### Code Reduction

The migration significantly reduced code complexity by leveraging protobuf4j's built-in capabilities:

| File | Before | After | Reduction |
|------|--------|-------|-----------|
| FileDescriptorUtils.java | 1,605 lines | 432 lines | **-73%** |
| ProtobufSchemaLoader.java | ~600 lines | ~300 lines | -50% |
| ProtobufSchemaParser.java | ~220 lines | ~140 lines | -36% |
| **Deleted files** | | | |
| DynamicSchema.java | 427 lines | 0 | -100% |
| EnumDefinition.java | 69 lines | 0 | -100% |
| MessageDefinition.java | 206 lines | 0 | -100% |
| ProtobufMessage.java | 147 lines | 0 | -100% |

**Net change across protobuf modules:** -512 lines (from git diff stats)

### Files Changed

The migration touched these primary modules:

1. **utils/protobuf-schema-utilities/**
   - `ProtobufSchemaLoader.java` - Core compilation using protobuf4j
   - `FileDescriptorUtils.java` - Simplified, removed Wire-specific code
   - `ProtobufFile.java` - Now uses protobuf4j for syntax validation
   - `ProtobufSchemaUtils.java` - New utility class replacing Wire helpers
   - `FileDescriptorToProtoConverter.java` - New: converts FileDescriptor to proto text

2. **schema-util/protobuf/**
   - `ProtobufContentCanonicalizer.java` - Uses protobuf4j normalization
   - `ProtobufContentValidator.java` - Uses protobuf4j for validation
   - `ProtobufReferenceFinder.java` - Works with FileDescriptor directly
   - `ProtobufDereferencer.java` - Simplified implementation

3. **serdes/generic/serde-common-protobuf/**
   - `ProtobufSchemaParser.java` - Uses FileDescriptor instead of ProtoFileElement
   - `ProtobufSerializer.java` - Minor updates

### API Changes

The migration removed all Wire-Schema AST types (`ProtoFileElement`, `MessageElement`, etc.) from the public API. Code that previously used:

```java
// Old Wire-Schema approach
ProtoFileElement fileElem = ProtoParser.parse(location, content);
MessageElement firstMessage = FileDescriptorUtils.firstMessage(fileElem);
```

Now uses:

```java
// New protobuf4j approach
FileDescriptor fd = ProtobufSchemaUtils.parseAndCompile(fileName, content, deps);
Descriptors.Descriptor firstMessage = ProtobufSchemaUtils.getFirstMessage(fd);
```

### Backward Compatibility Testing

The project includes a backward compatibility test module that:

1. Shades the old wire-schema based serializer (v3.1.2) into a separate namespace
2. Runs both old and new serializers against the same registry instance
3. Verifies that messages serialized with the old serializer can be deserialized by the new one and vice versa

This ensures a smooth upgrade path for existing Apicurio Registry users.

## Conclusion

The migration from Wire-Schema to protobuf4j represents a significant architectural improvement:

1. **Correctness**: By using the actual `protoc` compiler (via WASM), we eliminate any semantic drift from the official protobuf specification.

2. **Reduced Dependencies**: Removing the Kotlin ecosystem and ICU4J significantly reduces the dependency footprint.

3. **Better Canonicalization**: The `normalizeSchemaToText()` function provides guaranteed-consistent canonical forms.

4. **Future-Proof**: As protoc evolves, protobuf4j can simply update the WASM binary without reimplementing parsing logic.

The trade-off of slightly higher compilation latency (due to WASM) is acceptable given the improved correctness and reduced maintenance burden.
