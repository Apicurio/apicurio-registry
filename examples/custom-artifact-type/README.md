# Custom Artifact Type Example

This example demonstrates how to extend Apicurio Registry with support for a custom artifact type at
deployment time. The example implements a simple TOML (Tom's Obvious, Minimal Language) configuration
file artifact type.

## Overview

Apicurio Registry can be extended to support custom artifact types by:
1. Implementing artifact type functions in JavaScript/TypeScript
2. Configuring the registry to load your custom artifact type
3. Deploying the registry with the custom configuration

This example includes:
- A TypeScript implementation of a TOML artifact type using the `@apicurio/artifact-type-builtins` npm package
- Configuration file for enabling the custom artifact type
- Docker Compose setup for running the registry with the custom type
- Demo script showing the custom artifact type in action

## Custom Artifact Type Components

The TOML artifact type implements the following components:

### Content Accepter
Automatically detects if content is TOML format based on content analysis.

### Content Validator
Validates that TOML content follows proper syntax rules.

### Content Canonicalizer
Normalizes TOML content for consistent comparison (uppercases keys).

### Compatibility Checker
Ensures backward compatibility by checking that sections haven't been removed.

### Content Dereferencer
Resolves `@include "filename"` references by replacing them with actual content.

### Reference Finder
Identifies external references in TOML content.

## Prerequisites

- Node.js 18+ and npm (for building the TypeScript implementation)
- Docker and Docker Compose
- curl and jq (for running the demo script)

## TypeScript Types and Utilities

This example uses the `@apicurio/artifact-type-builtins` npm package, which provides:
- TypeScript type definitions for all artifact type function interfaces
- Built-in utility functions like `info()` and `debug()` for logging
- IDE autocomplete support for developing custom artifact types

The package is published on npm and can be installed with:
```bash
npm install @apicurio/artifact-type-builtins
```

For more information, see: https://www.npmjs.com/package/@apicurio/artifact-type-builtins

## Quick Start

### 1. Build the Custom Artifact Type

First, build the JavaScript library from the TypeScript source:

```bash
cd toml-artifact-type
npm install
npm run build
cd ..
```

This will create `toml-artifact-type/dist/toml-artifact-type.js` which contains the bundled artifact
type implementation.

### 2. Start the Registry

Start Apicurio Registry with the custom artifact type configuration:

```bash
docker compose up
```

The Docker Compose file:
- Starts Apicurio Registry with custom artifact type support (using in-memory storage)
- Starts Apicurio Registry UI for web-based management
- Mounts the configuration file and JavaScript library into the registry container

Wait for the services to be ready (check with `docker compose logs -f`).

**Note**: This example uses in-memory storage for simplicity. Data will be lost when the container
stops. For production use, configure persistent storage (PostgreSQL, Kafka, etc.).

### 3. Run the Demo

Execute the demo script to see the custom artifact type in action:

```bash
./demo.sh
```

The demo script demonstrates:
- Listing available artifact types (including TOML)
- Creating TOML artifacts
- Auto-detection of artifact type from content
- Creating new versions with compatibility checking
- Content validation
- Handling incompatible changes

### 4. Explore the Registry

You can also explore the registry using:
- **Web UI**: http://localhost:8888
- **REST API**: http://localhost:8080/apis/registry/v3

## Configuration Details

### artifact-types-config.json

This file configures the custom artifact type:

```json
{
  "includeStandardArtifactTypes": true,
  "artifactTypes": [
    {
      "artifactType": "TOML",
      "name": "TOML",
      "description": "Tom's Obvious, Minimal Language - A config file format",
      "contentTypes": ["application/toml"],
      "scriptLocation": "/custom-artifact-types/dist/toml-artifact-type.js",
      "contentAccepter": { "type": "script" },
      "contentCanonicalizer": { "type": "script" },
      "contentValidator": { "type": "script" },
      "compatibilityChecker": { "type": "script" },
      "contentDereferencer": { "type": "script" },
      "referenceFinder": { "type": "script" }
    }
  ]
}
```

Key fields:
- `includeStandardArtifactTypes`: Set to `true` to keep built-in types (Avro, Protobuf, etc.)
- `artifactType`: Unique identifier for your custom type
- `contentTypes`: MIME types associated with this artifact type
- `scriptLocation`: Path to the JavaScript implementation (inside the container)
- Component configurations: Each set to `"type": "script"` to use the JavaScript implementation

### Docker Compose Configuration

The Docker Compose file mounts two important paths:

```yaml
volumes:
  - ./artifact-types-config.json:/custom-artifact-types/artifact-types-config.json:ro
  - ./toml-artifact-type/dist:/custom-artifact-types/dist:ro
```

And sets the environment variable:

```yaml
environment:
  APICURIO_ARTIFACT_TYPES_CONFIG_FILE: "/custom-artifact-types/artifact-types-config.json"
```

**Note**: The example uses in-memory storage (the default). To use persistent storage, add database
configuration environment variables.

## Cleanup

To stop and remove the containers:

```bash
docker compose down
```

Since this example uses in-memory storage, all data is automatically removed when the containers
stop.

## Advanced Topics

### Using with Kubernetes

To deploy with Kubernetes:
1. Build the JavaScript library
2. Create a ConfigMap from `artifact-types-config.json` and the JavaScript library
3. Mount the ConfigMap into the registry pod
4. Set the `APICURIO_ARTIFACT_TYPES_CONFIG_FILE` environment variable

### Disabling Standard Artifact Types

If you want only your custom types (no Avro, Protobuf, etc.):

```json
{
  "includeStandardArtifactTypes": false,
  "artifactTypes": []
}
```

### Multiple Custom Artifact Types

You can define multiple custom types in the same configuration file:

```json
{
  "includeStandardArtifactTypes": true,
  "artifactTypes": [
    {},
    {},
    {}
  ]
}
```

## References

- [Apicurio Registry Documentation](https://www.apicur.io/registry/)
- [Custom Artifact Types Test](../../app/src/test/java/io/apicurio/registry/customTypes/)
- [TOML Specification](https://toml.io/)

## Notes

This example keeps the TOML parsing and validation intentionally simple to focus on demonstrating
the custom artifact type mechanism. In a production implementation, you would:
- Use a proper TOML parsing library
- Implement comprehensive validation
- Add sophisticated compatibility checking logic
- Handle edge cases and error conditions more robustly