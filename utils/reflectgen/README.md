# Reflection Registry Generator

This module contains a build-time utility for generating the GraalVM native image reflection registry.

## Purpose

When compiling Apicurio Registry to a GraalVM native image, certain classes from `apicurio-data-models`
need to be registered for reflection. This module scans the classpath and automatically generates the
`ApicurioRegisterForReflection` class that contains all required class registrations.

## How It Works

1. This module is compiled early in the build (before the `app` module)
2. The `app` module uses `exec-maven-plugin` to run `ReflectionRegistryGenerator` during `generate-sources`
3. The generator scans for all subclasses of:
   - `io.apicurio.datamodels.validation.ValidationRule`
   - `io.apicurio.datamodels.models.Node`
4. It generates `ApicurioRegisterForReflection.java` into `app/target/generated-sources/reflection/`
5. The generated class is compiled with the rest of the `app` module

## Benefits

- **Automatic**: No manual maintenance required
- **Always Current**: Regenerated on every clean build
- **Version Synchronized**: Uses the exact `apicurio-data-models` version from the parent pom
- **Build-Time Only**: Not included in runtime dependencies

## Dependencies

- `org.reflections:reflections` - For classpath scanning
- `io.apicurio:apicurio-data-models` - The library being scanned (provided scope)

## Usage

This module is used automatically during the Maven build. No manual intervention required.

To see the generator in action:

```bash
cd app
mvn clean compile -X | grep "ReflectionRegistryGenerator"
```

You should see output like:
```
Successfully generated ApicurioRegisterForReflection at .../target/generated-sources/reflection/...
```
