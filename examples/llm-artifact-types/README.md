# LLM/AI Model Schema Artifact Types

This example demonstrates the built-in AI/ML artifact types in Apicurio Registry for managing LLM schemas and prompt templates.

## Overview

Apicurio Registry includes built-in support for AI/ML-related artifact types:

- **MODEL_SCHEMA**: AI/ML model input/output schema definitions and metadata
- **PROMPT_TEMPLATE**: Version-controlled prompt templates with variable schemas

These artifact types are available by default - no configuration required.

## Artifact Types

### MODEL_SCHEMA

Defines and validates AI/ML model input/output schemas and metadata.

**Content Types**: `application/json`, `application/x-yaml`

**Key Features**:
- Auto-detection from content structure (`modelId` + `input`/`output` fields)
- JSON Schema validation for input/output definitions
- Backward compatibility checking (cannot remove required fields, change types)
- Canonicalization for consistent comparisons
- Reference resolution for `$ref` schemas

**Example**:
```json
{
  "$schema": "https://apicur.io/schemas/model-schema/v1",
  "modelId": "gpt-4-turbo",
  "provider": "openai",
  "version": "2024-01",
  "input": {
    "type": "object",
    "properties": {
      "messages": { "type": "array" },
      "temperature": { "type": "number", "minimum": 0, "maximum": 2 }
    },
    "required": ["messages"]
  },
  "output": {
    "type": "object",
    "properties": {
      "choices": { "type": "array" },
      "usage": { "type": "object" }
    }
  },
  "metadata": {
    "contextWindow": 128000,
    "capabilities": ["chat", "function_calling", "vision"]
  }
}
```

### PROMPT_TEMPLATE

Version-controlled prompt templates with variable schemas for LLMOps.

**Content Types**: `application/x-yaml`, `application/json`, `text/x-prompt-template`

**Key Features**:
- Template variable extraction and validation (`{{variable}}` syntax)
- Variable schema definitions with types, constraints, and defaults
- Backward compatibility (cannot remove variables, change types)
- Output schema specification
- Metadata for model recommendations and token estimation

**Example**:
```yaml
$schema: https://apicur.io/schemas/prompt-template/v1
templateId: summarization-v1
name: Document Summarization
version: "1.0"

template: |
  Style: {{style}}
  Maximum length: {{max_words}} words

  Document: {{document}}

  Please provide a {{style}} summary.

variables:
  style:
    type: string
    enum: [concise, detailed, bullet-points]
    default: concise
  max_words:
    type: integer
    minimum: 50
    maximum: 1000
    default: 200
  document:
    type: string
    required: true

metadata:
  recommendedModels: [gpt-4-turbo, claude-3-opus]
```

## Quick Start

### 1. Start the Registry

Start Apicurio Registry (these types are built-in):

```bash
docker compose up
```

Wait for the services to be ready (check with `docker compose logs -f`).

### 2. Create a Prompt Template

```bash
curl -X POST http://localhost:8080/apis/registry/v3/groups/default/artifacts \
  -H "Content-Type: application/x-yaml" \
  -H "X-Registry-ArtifactId: summarization-v1" \
  -H "X-Registry-ArtifactType: PROMPT_TEMPLATE" \
  -d '
templateId: summarization-v1
template: "Summarize in {{style}} style: {{document}}"
variables:
  style:
    type: string
    enum: [concise, detailed]
  document:
    type: string
    required: true
'
```

### 3. Create a Model Schema

```bash
curl -X POST http://localhost:8080/apis/registry/v3/groups/default/artifacts \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: gpt-4-turbo" \
  -H "X-Registry-ArtifactType: MODEL_SCHEMA" \
  -d '{
    "modelId": "gpt-4-turbo",
    "provider": "openai",
    "input": {"type": "object", "properties": {"messages": {"type": "array"}}},
    "output": {"type": "object", "properties": {"choices": {"type": "array"}}},
    "metadata": {"contextWindow": 128000, "capabilities": ["chat", "vision"]}
  }'
```

### 4. Explore the Registry

- **Web UI**: http://localhost:8888
- **REST API**: http://localhost:8080/apis/registry/v3

## Compatibility Rules

### MODEL_SCHEMA Compatibility

When backward compatibility is enabled:

| Change | Allowed |
|--------|---------|
| Add optional input property | Yes |
| Add required input property | No |
| Remove input property | No |
| Change input property type | No |
| Add output property | Yes |
| Remove output property | No |
| Change output property type | No |

### PROMPT_TEMPLATE Compatibility

When backward compatibility is enabled:

| Change | Allowed |
|--------|---------|
| Add optional variable | Yes |
| Remove unused variable | Yes |
| Remove used variable | No |
| Change variable type | No |
| Make optional variable required | No |
| Narrow enum values | No |
| Change template text (same variables) | Yes |

## Use Cases

### LLMOps / Model Governance

- Track model input/output schemas across versions
- Ensure backward compatibility when updating models
- Document model capabilities and limitations
- Manage pricing and metadata

### Prompt Engineering

- Version-controlled prompt templates
- Variable validation ensures consistent usage
- Track prompt evolution over time
- Team collaboration on prompts

### RAG Pipelines

- Store embedding model configurations
- Manage vector store settings
- Link prompts to recommended models

## Sample Schemas

The `sample-schemas/` directory contains example artifacts:

- `gpt4-model-schema.json` - OpenAI GPT-4 Turbo model schema
- `claude-model-schema.json` - Anthropic Claude 3 Opus model schema
- `apicurio-support-system-prompt.yaml` - System prompt for support chatbot
- `apicurio-support-chat-prompt.yaml` - Chat prompt template

## Cleanup

To stop and remove the containers:

```bash
docker compose down
```

## REST API Endpoints

### Render Prompt Template

Server-side variable substitution with validation:

```bash
POST /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render

# Example
curl -X POST http://localhost:8080/apis/registry/v3/groups/default/artifacts/summarization-v1/versions/branch=latest/render \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "document": "The quick brown fox...",
      "style": "concise",
      "max_words": 100
    }
  }'
```

**Response:**

```json
{
  "rendered": "Style: concise\nMaximum length: 100 words\nDocument: The quick brown fox...",
  "groupId": "default",
  "artifactId": "summarization-v1",
  "version": "1.0",
  "validationErrors": []
}
```

## References

- [Apicurio Registry Documentation](https://www.apicur.io/registry/)
- [Custom Artifact Types Blog Post](https://www.apicur.io/blog/2025/10/27/custom-artifact-types)
- [Model Cards for Model Reporting](https://arxiv.org/abs/1810.03993)
- [Prompt Engineering Best Practices](https://platform.openai.com/docs/guides/prompt-engineering)
