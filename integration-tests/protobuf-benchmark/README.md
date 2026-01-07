# Protobuf Schema Parsing Benchmark

This module contains a standalone benchmark comparing Wire-schema vs protobuf4j schema parsing performance.

## Important: Apples vs Oranges

**This benchmark compares DIFFERENT operations:**

| Implementation | Returns | What it does |
|----------------|---------|--------------|
| Wire `ProtoParser.parse()` | `ProtoFileElement` | AST only (syntax tree) |
| protobuf4j `buildFileDescriptors()` | `FileDescriptor` | Fully resolved, validated |

**Wire's AST parsing is faster because it does LESS work.** To get a `FileDescriptor` from Wire, you need:
1. `ProtoParser.parse()` → AST
2. `SchemaLoader.loadSchema()` → Wire Schema
3. Manual conversion to `FileDescriptorProto` (~500 lines of code)
4. `FileDescriptor.buildFrom()` → final result

protobuf4j does all of this in one call via the actual Google protoc compiler.

## Running the Benchmark

```bash
cd integration-tests/protobuf-benchmark
mvn clean package exec:java
```

## Expected Results

```
================================================================================
PROTOBUF SCHEMA PARSING BENCHMARK
Wire-schema vs protobuf4j
================================================================================

IMPORTANT: This compares DIFFERENT operations:

  Wire:       ProtoParser.parse() -> ProtoFileElement (AST only)
  protobuf4j: buildFileDescriptors() -> FileDescriptor (fully resolved)

  RESULTS for Simple (UUID):
  ------------------------------------------------------------
  | Implementation | Per Operation   | 100 Operations     |
  |----------------|-----------------|--------------------
  | Wire (AST)     | 0.038 ms        | 4 ms               |
  | protobuf4j     | 2.889 ms        | 289 ms             |

  Wire AST is 76x faster (but does less work - AST only, not FileDescriptor)

  RESULTS for Complex (Order system):
  ------------------------------------------------------------
  | Implementation | Per Operation   | 100 Operations     |
  |----------------|-----------------|--------------------
  | Wire (AST)     | 0.349 ms        | 35 ms              |
  | protobuf4j     | 5.142 ms        | 514 ms             |

  Wire AST is 15x faster (but does less work - AST only, not FileDescriptor)
```

## Key Insight

The speed difference shrinks as schema complexity increases:
- Simple schema: Wire is ~76x faster
- Complex schema: Wire is ~15x faster

This is because Wire's AST parsing scales with schema size, while protobuf4j has fixed WASM overhead that becomes proportionally smaller for larger schemas.

## Why Use protobuf4j Then?

The performance gain in Apicurio Registry doesn't come from raw parsing speed. It comes from:

1. **Caching strategy**: The new implementation caches parsed results effectively
2. **100% protoc compatibility**: No parser drift - uses actual Google protoc compiled to WASM
3. **~50% dependency reduction**: No Kotlin, no ICU4J, no Okio
4. **Simpler code**: Deleted ~500 lines of complex Wire AST to FileDescriptorProto conversion code
5. **Correctness**: No risk of Wire's parser diverging from Google's canonical implementation

## The Real Performance Story

The 346x improvement in Apicurio Registry serdes comes from **architectural improvements**, not parser speed:

- **Old implementation**: Called expensive parsing operations on every serialize/deserialize
- **New implementation**: Caches FileDescriptors and only parses when schemas change

## Dependencies

- `protobuf4j-v4`: WASM-based protobuf compiler (Google's protoc compiled to WebAssembly)
- `wire-schema-jvm`: Square's Wire schema parser (for comparison)
- `protobuf-java`: Google's Protobuf Java runtime
