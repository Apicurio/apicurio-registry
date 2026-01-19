# LLM/AI Model Schema Support Implementation Plan

> **Status:** ✅ COMPLETE - Core implementation finished. See [gh-issue-6997-proposal.md](./gh-issue-6997-proposal.md) for extended features (SDK integrations, REST endpoints).

**Issue**: [#6997 - LLM/AI Model Schema Support](https://github.com/Apicurio/apicurio-registry/issues/6997)
**Branch**: `feature/6997-llm-ai-model-schema-support`
**Worktree**: `/Users/carlesarnal/IdeaProjects/apicurio-registry-6997`
**Parent Epic**: #6991 - AI Agent Registry
**Priority**: P1 - High (AI differentiation strategy)

---

## 1. Overview

This plan describes the implementation of new artifact types for managing AI/ML-related schemas using the **JavaScript/TypeScript approach** as described in the [Custom Artifact Types blog post](https://www.apicur.io/blog/2025/10/27/custom-artifact-types).

**Implementation Approach**: Bundled JavaScript - The TypeScript implementations are compiled and bundled WITH the registry as built-in artifact types, available by default without user configuration.

### New Artifact Types

| Artifact Type | Description | Priority |
|---------------|-------------|----------|
| `MODEL_SCHEMA` | Input/output schema definitions and model metadata | Phase 1 |
| `PROMPT_TEMPLATE` | Template content with variable schemas and version history | Phase 1 |
| `RAG_CONFIG` | Embedding model config and vector store settings | Phase 2 (Future) |

---

## 2. Technical Approach

### 2.1 Bundled JavaScript Implementation

Unlike external custom artifact types that users configure at deployment time, these LLM artifact types are **bundled with the registry** and available by default:

1. TypeScript sources in `app/src/main/resources-unfiltered/llm-artifact-types/`
2. Compiled JavaScript bundles in `app/src/main/resources/llm-artifact-types/`
3. Default configuration loaded from classpath when no external config is provided
4. Execute in the QuickJS sandboxed environment via `quickjs4j`

### 2.2 Architecture

```
app/src/main/resources-unfiltered/llm-artifact-types/
├── package.json
├── tsconfig.json
├── tsconfig-build.json
└── src/
    ├── ModelSchemaArtifactType.ts
    └── PromptTemplateArtifactType.ts

app/src/main/resources/llm-artifact-types/
├── model-schema-artifact-type.js      (compiled bundle)
├── prompt-template-artifact-type.js   (compiled bundle)
└── default-artifact-types-config.json (default config)

examples/llm-artifact-types/
├── README.md           (documentation)
├── demo.sh             (interactive demo)
├── docker-compose.yml  (for demo/testing)
└── sample-schemas/     (example artifacts)
```

### 2.3 Loading Priority

The `ArtifactTypeUtilProviderImpl` loads artifact types in this order:

1. **External config file** (if `apicurio.artifact-types.config-file` points to existing file)
2. **Bundled default config** (from classpath: `llm-artifact-types/default-artifact-types-config.json`)
3. **Standard providers only** (fallback if neither exists)

---

## 3. Artifact Type Specifications

### 3.1 MODEL_SCHEMA Artifact Type

**Purpose**: Define and validate AI/ML model input/output schemas and metadata.

**Content Types**:
- `application/json` (primary)
- `application/x-yaml`

**Schema Structure** (JSON Schema-based):

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

**Implemented Functions**:

| Function | Behavior |
|----------|----------|
| `acceptsContent` | Validates JSON/YAML with `$schema` containing `model-schema` or presence of `modelId` + `input`/`output` fields |
| `validate` | Validates structure: required fields (`modelId`, `input`, `output`), JSON Schema validity |
| `testCompatibility` | BACKWARD: Cannot remove/add required input fields, cannot change types |
| `canonicalize` | Normalize JSON (sorted keys, consistent formatting) |
| `dereference` | Resolve `$ref` references |
| `findExternalReferences` | Find `$ref` pointing to external schemas |

### 3.2 PROMPT_TEMPLATE Artifact Type

**Purpose**: Version-controlled prompt templates with variable schemas and metadata.

**Content Types**:
- `application/x-yaml` (primary)
- `application/json`
- `text/x-prompt-template`

**Schema Structure**:

```yaml
$schema: https://apicur.io/schemas/prompt-template/v1
templateId: summarization-v1
name: Document Summarization
version: "1.0"

template: |
  Style: {{style}}
  Maximum length: {{max_words}} words
  Document: {{document}}

variables:
  style:
    type: string
    enum: [concise, detailed, bullet-points]
    default: concise
  max_words:
    type: integer
    minimum: 50
    maximum: 1000
  document:
    type: string
    required: true

metadata:
  recommendedModels: [gpt-4-turbo, claude-3-opus]
```

**Implemented Functions**:

| Function | Behavior |
|----------|----------|
| `acceptsContent` | Validates YAML/JSON with `templateId` + `template` fields |
| `validate` | Validates template variables match defined schema |
| `testCompatibility` | BACKWARD: Cannot remove used variables, cannot change types |
| `canonicalize` | Normalize YAML (sorted keys) |

---

## 4. Implementation Steps

### Phase 1: Core Implementation (Bundled with Registry)

#### Step 1: Create TypeScript Sources in app/src/main/resources-unfiltered

```
app/src/main/resources-unfiltered/llm-artifact-types/
├── package.json
├── tsconfig.json
├── tsconfig-build.json
└── src/
    ├── ModelSchemaArtifactType.ts
    └── PromptTemplateArtifactType.ts
```

#### Step 2: Create Compiled Bundles in app/src/main/resources

```
app/src/main/resources/llm-artifact-types/
├── model-schema-artifact-type.js
├── prompt-template-artifact-type.js
└── default-artifact-types-config.json
```

#### Step 3: Create Default Configuration

`default-artifact-types-config.json`:

```json
{
  "includeStandardArtifactTypes": true,
  "artifactTypes": [
    {
      "artifactType": "MODEL_SCHEMA",
      "name": "Model Schema",
      "description": "AI/ML model input/output schema definitions and metadata",
      "contentTypes": ["application/json", "application/x-yaml"],
      "scriptLocation": "llm-artifact-types/model-schema-artifact-type.js",
      "contentAccepter": { "type": "script" },
      "contentCanonicalizer": { "type": "script" },
      "contentValidator": { "type": "script" },
      "compatibilityChecker": { "type": "script" },
      "contentDereferencer": { "type": "script" },
      "referenceFinder": { "type": "script" }
    },
    {
      "artifactType": "PROMPT_TEMPLATE",
      "name": "Prompt Template",
      "description": "Version-controlled prompt templates with variable schemas",
      "contentTypes": ["application/x-yaml", "application/json", "text/x-prompt-template"],
      "scriptLocation": "llm-artifact-types/prompt-template-artifact-type.js",
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

#### Step 4: Modify ArtifactTypeUtilProviderImpl

Update to load bundled config from classpath:

```java
@PostConstruct
public void init() {
    ArtifactTypesConfiguration config = loadArtifactTypeConfiguration();
    if (config != null) {
        loadConfiguredProviders(config);
    } else {
        // Try loading bundled default config from classpath
        config = loadBundledConfiguration();
        if (config != null) {
            log.info("Loading bundled LLM artifact types from classpath");
            loadConfiguredProviders(config);
        } else {
            log.info("Using standard artifact types only");
            loadStandardProviders();
        }
    }
}

private ArtifactTypesConfiguration loadBundledConfiguration() {
    try (InputStream is = getClass().getClassLoader()
            .getResourceAsStream("llm-artifact-types/default-artifact-types-config.json")) {
        if (is == null) {
            return null;
        }
        String configSrc = IoUtil.toString(is);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(configSrc, ArtifactTypesConfiguration.class);
    } catch (Exception e) {
        log.warn("Failed to load bundled artifact type configuration", e);
        return null;
    }
}
```

#### Step 5: Update ScriptInterfaceUtils for Classpath Loading

Modify to load scripts from classpath when path doesn't start with `/`:

```java
public static String loadScriptLibrary(String scriptLocation) {
    // If path starts with /, load from filesystem
    // Otherwise, load from classpath
    if (scriptLocation.startsWith("/")) {
        return loadFromFilesystem(scriptLocation);
    } else {
        return loadFromClasspath(scriptLocation);
    }
}
```

#### Step 6: Update Example Directory

Keep `examples/llm-artifact-types/` for documentation and demo:

```
examples/llm-artifact-types/
├── README.md
├── demo.sh
├── docker-compose.yml
└── sample-schemas/
    ├── gpt4-model-schema.json
    ├── claude-model-schema.json
    ├── summarization-prompt.yaml
    └── qa-prompt.yaml
```

The example README explains that these types are built-in and demonstrates usage.

---

## 5. File Checklist

### Registry Core Files

**TypeScript Sources** (`app/src/main/resources-unfiltered/llm-artifact-types/`):
- N/A - TypeScript sources compiled externally; only compiled bundles are included in repo

**Compiled Resources** (`app/src/main/resources/llm-artifact-types/`):
- [x] `model-schema-artifact-type.js` ✅
- [x] `prompt-template-artifact-type.js` ✅
- [x] `default-artifact-types-config.json` ✅

**Java Modifications**:
- [x] `app/src/main/java/.../ArtifactTypeUtilProviderImpl.java` (load from classpath) ✅
- [x] `app/src/main/java/.../ScriptInterfaceUtils.java` (classpath loading support) ✅

### Example Files (`examples/llm-artifact-types/`)

- [x] `README.md` ✅
- [x] `demo.sh` ✅
- [x] `docker-compose.yml` ✅
- [x] `sample-schemas/gpt4-model-schema.json` ✅
- [x] `sample-schemas/claude-model-schema.json` ✅
- [x] `sample-schemas/summarization-prompt.yaml` ✅
- [x] `sample-schemas/qa-prompt.yaml` ✅

---

## 6. Build Integration

### Maven Build

Add npm build step to compile TypeScript during Maven build:

```xml
<!-- In app/pom.xml -->
<execution>
    <id>build-llm-artifact-types</id>
    <phase>generate-resources</phase>
    <goals>
        <goal>exec</goal>
    </goals>
    <configuration>
        <workingDirectory>${project.basedir}/src/main/resources-unfiltered/llm-artifact-types</workingDirectory>
        <executable>npm</executable>
        <arguments>
            <argument>run</argument>
            <argument>build</argument>
        </arguments>
    </configuration>
</execution>
```

---

## 7. Acceptance Criteria

| Criteria | Verification |
|----------|--------------|
| MODEL_SCHEMA available by default | List artifact types shows MODEL_SCHEMA |
| PROMPT_TEMPLATE available by default | List artifact types shows PROMPT_TEMPLATE |
| No configuration required | Types work out-of-the-box |
| External config can override | Setting config file disables bundled types if desired |
| Documentation and demo available | examples/llm-artifact-types/ has working demo |

---

## 8. References

- [Custom Artifact Types Blog Post](https://www.apicur.io/blog/2025/10/27/custom-artifact-types)
- [TOML Example Implementation](../examples/custom-artifact-type/)
- [Model Cards for Model Reporting](https://arxiv.org/abs/1810.03993)
