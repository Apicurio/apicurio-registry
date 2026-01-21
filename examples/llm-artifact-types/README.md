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

### 2. Run the Demo

Execute the demo script to see the artifact types in action:

```bash
./demo.sh
```

The demo demonstrates:
- Listing available artifact types (MODEL_SCHEMA, PROMPT_TEMPLATE)
- Creating artifacts with explicit types
- Content auto-detection
- Backward compatibility checking
- Content validation

### 3. Explore the Registry

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
- `summarization-prompt.yaml` - Document summarization prompt template
- `qa-prompt.yaml` - Question & Answer RAG prompt template

## Cleanup

To stop and remove the containers:

```bash
docker compose down
```

## SDK Integrations

### Python SDK

Install with LLM support:

```bash
pip install apicurioregistrysdk[llm]
```

**Using PromptRegistry:**

```python
from apicurioregistrysdk.llm import PromptRegistry

# Initialize registry
registry = PromptRegistry("http://localhost:8080", group_id="default")

# Fetch versioned prompt template
prompt = await registry.get_prompt_async("summarization-v1", version="1.0")

# Render with variables
rendered = prompt.render(document="...", style="concise", max_words=200)

# Server-side rendering with validation
rendered = await registry.render_server_side_async("summarization-v1", {
    "document": "...",
    "style": "concise"
})
```

**Using ModelRegistry:**

```python
from apicurioregistrysdk.llm import ModelRegistry

registry = ModelRegistry("http://localhost:8080")

# Search by capabilities
results = await registry.search_async(
    capabilities=["vision", "tool_use"],
    provider="openai",
    min_context_window=100000
)

# Compare models
comparison = await registry.compare_models_async(["gpt-4-turbo", "claude-3-opus"])
```

**LangChain Integration:**

```python
from apicurioregistrysdk.llm.langchain import ApicurioPromptTemplate

prompt = ApicurioPromptTemplate(
    registry_url="http://localhost:8080",
    artifact_id="summarization-v1"
)
result = prompt.format(document="...", style="concise")
```

### Java SDK (Quarkus + LangChain4j)

Add the dependencies:

```xml
<!-- Apicurio Registry LangChain4j Integration -->
<dependency>
    <groupId>io.apicurio</groupId>
    <artifactId>apicurio-registry-langchain4j</artifactId>
    <version>${apicurio.version}</version>
</dependency>

<!-- LLM Provider (choose one) -->
<!-- Option 1: Ollama (free, local) - Recommended for development -->
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-ollama</artifactId>
    <version>1.5.0</version>
</dependency>

<!-- Option 2: OpenAI (paid API) -->
<!--
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-openai</artifactId>
    <version>1.5.0</version>
</dependency>
-->
```

**Using with CDI:**

```java
@Inject
ApicurioPromptRegistry promptRegistry;

@Inject
ChatModel chatModel;  // Automatically configured by quarkus-langchain4j

public String chat(String question) {
    // Fetch versioned prompt from registry
    ApicurioPromptTemplate template = promptRegistry.getPrompt("qa-assistant", "1.2");

    // Apply variables and get rendered prompt
    Prompt prompt = template.apply(Map.of(
        "question", question,
        "context", getContext()
    ));

    // Send to LLM
    return chatModel.chat(prompt.text());
}
```

**Configuration (application.properties):**

```properties
# Registry connection
apicurio.registry.url=http://localhost:8080
apicurio.registry.default-group=default

# Ollama configuration (free, local LLM) - Recommended
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=llama3.2
quarkus.langchain4j.ollama.timeout=120s

# OR OpenAI configuration (requires API key and credits)
# quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
# quarkus.langchain4j.openai.chat-model.model-name=gpt-4o
```

**Running Ollama locally:**

```bash
# macOS
brew install ollama && brew services start ollama && ollama pull llama3.2

# Linux
curl -fsSL https://ollama.com/install.sh | sh && ollama serve & && ollama pull llama3.2
```

See the [quarkus-demo](./quarkus-demo/) directory for a complete working example.

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

### Search Models

Query MODEL_SCHEMA artifacts by capabilities:

```bash
GET /apis/registry/v3/search/models?capability=function_calling&provider=openai&minContextWindow=100000

# Example
curl "http://localhost:8080/apis/registry/v3/search/models?capability=vision&provider=openai"
```

**Response:**

```json
{
  "count": 1,
  "models": [
    {
      "groupId": "ai-models",
      "artifactId": "gpt-4-turbo",
      "version": "2024-01",
      "name": "GPT-4 Turbo",
      "provider": "openai",
      "contextWindow": 128000,
      "capabilities": ["chat", "function_calling", "vision"],
      "modelId": "gpt-4-turbo"
    }
  ]
}
```

## References

- [Apicurio Registry Documentation](https://www.apicur.io/registry/)
- [Custom Artifact Types Blog Post](https://www.apicur.io/blog/2025/10/27/custom-artifact-types)
- [Model Cards for Model Reporting](https://arxiv.org/abs/1810.03993)
- [Prompt Engineering Best Practices](https://platform.openai.com/docs/guides/prompt-engineering)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [LangChain Python Documentation](https://python.langchain.com/)
