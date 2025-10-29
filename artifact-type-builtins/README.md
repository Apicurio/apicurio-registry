# @apicurio/artifact-type-builtins

Built-in functions and TypeScript type definitions for creating custom artifact types in Apicurio
Registry.

## Overview

This package provides the TypeScript type definitions and built-in utility functions needed to develop
custom artifact types for [Apicurio Registry](https://www.apicur.io/registry/). It enables type safety
and IDE autocomplete support when implementing custom artifact type handlers in JavaScript/TypeScript.

## Installation

```bash
npm install @apicurio/artifact-type-builtins
```

## Usage

Import the types and built-in functions in your custom artifact type implementation:

```typescript
import {
    ArtifactTypeScriptProvider,
    info,
    debug
} from '@apicurio/artifact-type-builtins';

export function acceptsContent(request: any): boolean {
    info('Checking if content is accepted');
    // Your implementation here
    return true;
}

export function validate(request: any): any {
    debug('Validating artifact content');
    // Your implementation here
    return {
        ruleViolations: []
    };
}

// Implement other required functions...
```

## Example

For a complete example of creating a custom artifact type, see the
[TOML Artifact Type Example](https://github.com/Apicurio/apicurio-registry/tree/main/examples/custom-artifact-type)
in the Apicurio Registry repository.

## License

Apache License 2.0
