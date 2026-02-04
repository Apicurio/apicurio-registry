# LLM Artifact Types Design: JSON Schema Backing + MCP + Standards Alignment

**Issue**: [#7254](https://github.com/Apicurio/apicurio-registry/issues/7254)
**Parent Epic**: [#6991](https://github.com/Apicurio/apicurio-registry/issues/6991) (AI Agent Registry - MCP & A2A Support)
**Last Updated**: 2026-02-04

---

## Overview

This document describes the design for formal JSON Schema backing for LLM artifact types (`PROMPT_TEMPLATE` and `MODEL_SCHEMA`), MCP (Model Context Protocol) integration, and alignment with industry standards.

---

## Industry Standards Alignment

Based on [research into existing standards](/claudedocs/research_prompt_model_standards_20260204.md), the implementation aligns with:

### PROMPT_TEMPLATE → Microsoft Prompty

[Prompty](https://prompty.ai/specification/page/) is Microsoft's open standard for LLM prompt templates.

| Apicurio Field | Prompty Field | Mapping |
|----------------|---------------|---------|
| `templateId` | `name` | Primary identifier |
| `name` | `name` | Display name |
| `description` | `description` | Direct |
| `version` | `version` | Direct |
| `variables` | `inputs` | Input parameters |
| `outputSchema` | `outputs` | Output format |
| `template` | Body content | Template with {{variables}} |
| `model` | `model` | Model configuration |
| `authors` | `authors` | Contributors |
| `tags` | `tags` | Categorization |

### MODEL_SCHEMA → Model Card Toolkit + MLflow

- **[Google Model Card Toolkit](https://github.com/tensorflow/model-card-toolkit)**: Comprehensive model documentation for governance
- **[MLflow Model Signatures](https://mlflow.org/docs/latest/ml/model/)**: Input/output schema definitions

| Apicurio Field | Model Card Field | Mapping |
|----------------|-----------------|---------|
| `modelId` | `model_details.name` | Model identifier |
| `provider` | `model_details.owners` | Model provider |
| `version` | `model_details.version` | Version info |
| `input` | `model_parameters.input_format` | I/O schema |
| `output` | `model_parameters.output_format` | I/O schema |
| `modelDetails` | `model_details` | Full metadata |
| `quantitativeAnalysis` | `quantitative_analysis` | Metrics |
| `considerations` | `considerations` | Ethics, limitations |
| `signature` | MLflow signature | MLflow compatibility |

---

## Schema Locations

```
app/llm-artifact-types-src/schemas/
├── prompt-template-v1.schema.json   # PROMPT_TEMPLATE JSON Schema
└── model-schema-v1.schema.json      # MODEL_SCHEMA JSON Schema
```

**Published URLs**:
- `https://apicur.io/schemas/prompt-template/v1`
- `https://apicur.io/schemas/model-schema/v1`

---

## PROMPT_TEMPLATE Schema

### Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `templateId` | string | Unique identifier for the template |
| `template` | string | The prompt template with {{variable}} placeholders |

### Optional Fields (Prompty-aligned)

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Human-friendly display name |
| `description` | string | Detailed description |
| `version` | string | Version identifier |
| `authors` | string[] | Contributors (Prompty) |
| `tags` | string[] | Categorization tags (Prompty) |
| `templateFormat` | enum | Template engine: `mustache`, `jinja2`, `handlebars` |
| `variables` | object | Variable schema definitions |
| `inputs` | object | Alternative to `variables` (Prompty naming) |
| `outputSchema` | object | Expected output format |
| `outputs` | object | Alternative to `outputSchema` (Prompty naming) |
| `model` | object | Model configuration (Prompty) |
| `metadata` | object | Additional metadata |
| `mcp` | object | MCP integration configuration |

### Model Configuration (Prompty-aligned)

```yaml
model:
  api: chat  # or "completion"
  configuration:
    type: azure_openai  # or "openai"
    azure_deployment: gpt-4
    azure_endpoint: https://...
  parameters:
    temperature: 0.7
    max_tokens: 1000
    top_p: 1.0
  response: first  # or "all"
```

### MCP Extension

```yaml
mcp:
  enabled: true
  name: my-prompt  # MCP prompt name (defaults to templateId)
  description: ...  # MCP prompt description
  arguments:        # MCP arguments (auto-derived from variables if omitted)
    - name: question
      description: User question
      required: true
```

---

## MODEL_SCHEMA Schema

### Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `modelId` | string | Unique model identifier |
| `input` or `output` | object | At least one must be present |

### Optional Fields (Model Card-aligned)

| Field | Type | Description |
|-------|------|-------------|
| `provider` | string | Model provider/vendor |
| `version` | string | Version identifier |
| `input` | object | JSON Schema for model input |
| `output` | object | JSON Schema for model output |
| `metadata` | object | Additional metadata |
| `definitions` | object | Reusable schema definitions |
| `modelDetails` | object | Model Card model_details section |
| `modelParameters` | object | Model Card model_parameters section |
| `quantitativeAnalysis` | object | Performance metrics |
| `considerations` | object | Ethics, limitations, use cases |
| `signature` | object | MLflow model signature |

### Model Details (Model Card-aligned)

```yaml
modelDetails:
  name: claude-3-opus
  overview: Advanced multimodal AI assistant
  owners:
    - name: Anthropic
      contact: support@anthropic.com
  version:
    name: "2024-02"
    date: 2024-02-01
  licenses:
    - identifier: MIT
  references:
    - reference: https://docs.anthropic.com
  regulatoryRequirements:
    - EU AI Act
    - NIST AI RMF
```

### Considerations (Model Card-aligned)

```yaml
considerations:
  users:
    - Developers
    - Enterprises
  useCases:
    - Chat applications
    - Content generation
  limitations:
    - May produce inaccurate information
    - Limited knowledge after training cutoff
  ethicalConsiderations:
    - name: Bias in training data
      mitigationStrategy: Regular auditing and testing
```

---

## MCP Integration Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Apicurio Registry                        │
├─────────────────────────────────────────────────────────────┤
│  PROMPT_TEMPLATE Artifacts                                   │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ templateId: support-chat                            │    │
│  │ mcp:                                                │    │
│  │   enabled: true                                     │    │
│  │   name: support-chat                                │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    MCP Server Module                         │
├─────────────────────────────────────────────────────────────┤
│  PromptTemplateMCPServer.java                               │
│  ├── list_mcp_prompts()     → List MCP-enabled templates    │
│  ├── get_mcp_prompt()       → Render template with args     │
│  ├── render_prompt_template()→ Render any template          │
│  └── render_registry_prompt()→ MCP @Prompt endpoint         │
│                                                              │
│  PromptTemplateConverter.java                               │
│  ├── parseContent()         → Parse JSON/YAML               │
│  ├── toMCPPrompt()          → Convert to MCP Prompt format  │
│  └── renderTemplate()       → Variable substitution         │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     AI Agent / LLM                           │
│  (Claude, ChatGPT, Llama, etc.)                             │
│                                                              │
│  MCP Protocol:                                               │
│  - prompts/list → Discover available prompts                 │
│  - prompts/get  → Get prompt content with arguments          │
└─────────────────────────────────────────────────────────────┘
```

---

## Implementation Phases

### Phase 1: JSON Schemas (✅ Complete)

- [x] Create `prompt-template-v1.schema.json` with Prompty alignment
- [x] Create `model-schema-v1.schema.json` with Model Card alignment
- [x] Add MCP extension to prompt template schema
- [x] Document field mappings to industry standards

### Phase 2: MCP Integration (✅ Complete)

- [x] Create `PromptTemplateConverter.java` for parsing/rendering
- [x] Create `PromptTemplateMCPServer.java` with MCP tools
- [x] Add `mcp-converter.ts` TypeScript utility
- [x] Update sample prompts with MCP extension

### Phase 3: Schema Publication (🔜 Pending)

- [ ] Deploy schemas to `https://apicur.io/schemas/`
- [ ] Add `/.well-known/schemas/` registry endpoint
- [ ] Configure CORS for schema access

### Phase 4: Validation Enhancement (🔜 Pending)

- [ ] Add JSON Schema validation to TypeScript artifact types
- [ ] Document correspondence between imperative and schema validation
- [ ] Add validation tests against sample schemas

### Phase 5: Import/Export Support (🔜 Future)

- [ ] Prompty file format import/export
- [ ] Model Card JSON export
- [ ] MLflow signature export
- [ ] Hugging Face model card compatibility

---

## File Locations

### TypeScript Source

```
app/llm-artifact-types-src/
├── schemas/
│   ├── prompt-template-v1.schema.json
│   └── model-schema-v1.schema.json
├── src/
│   ├── PromptTemplateArtifactType.ts
│   ├── ModelSchemaArtifactType.ts
│   ├── mcp-converter.ts
│   └── shared-utils.ts
└── package.json
```

### Java MCP Module

```
mcp/src/main/java/io/apicurio/registry/mcp/
├── PromptTemplateConverter.java
├── Descriptions.java
├── RegistryService.java
└── servers/
    ├── PromptTemplateMCPServer.java
    ├── MCPPrompts.java
    └── ...
```

### Sample Schemas

```
examples/llm-artifact-types/sample-schemas/
├── apicurio-support-chat-prompt.yaml     # With MCP extension
├── apicurio-support-system-prompt.yaml
├── claude-model-schema.json
└── gpt4-model-schema.json
```

---

## Build Commands

```bash
# Build TypeScript artifact types
cd app/llm-artifact-types-src
npm install
npm run build

# Build MCP module
./mvnw clean install -DskipTests -pl mcp -am

# Run MCP server
cd mcp
../mvnw quarkus:dev
```

---

## References

- [Microsoft Prompty Specification](https://prompty.ai/specification/page/)
- [Google Model Card Toolkit](https://github.com/tensorflow/model-card-toolkit)
- [MLflow Model Signatures](https://mlflow.org/docs/latest/ml/model/)
- [MCP Prompts Specification](https://modelcontextprotocol.io/specification/2025-06-18/server/prompts)
- [JSON Schema Draft 2020-12](https://json-schema.org/draft/2020-12/schema)
- [Research Document](/claudedocs/research_prompt_model_standards_20260204.md)
