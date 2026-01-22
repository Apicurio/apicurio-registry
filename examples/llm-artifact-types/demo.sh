#!/bin/bash

# Demo script for LLM/AI Model Schema artifact types in Apicurio Registry
# This script demonstrates the various capabilities of the custom artifact types

REGISTRY_URL="http://localhost:8080/apis/registry/v3"
GROUP_ID="ai-models"

echo "=================================================="
echo "LLM/AI Model Schema Artifact Types Demo"
echo "=================================================="
echo ""

# Step 1: List available artifact types
echo "1. Listing available artifact types..."
echo "   Command: curl -s $REGISTRY_URL/admin/config/artifactTypes"
curl -s "$REGISTRY_URL/admin/config/artifactTypes" | jq '.'
echo ""
echo "   Note: You should see MODEL_SCHEMA and PROMPT_TEMPLATE in the list above"
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 2: Create a MODEL_SCHEMA artifact (GPT-4 Turbo)
echo "2. Creating MODEL_SCHEMA artifact (GPT-4 Turbo)..."
MODEL_SCHEMA_CONTENT='{
  "$schema": "https://apicur.io/schemas/model-schema/v1",
  "modelId": "gpt-4-turbo",
  "provider": "openai",
  "version": "2024-01",
  "input": {
    "type": "object",
    "properties": {
      "messages": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "role": { "type": "string", "enum": ["system", "user", "assistant"] },
            "content": { "type": "string" }
          },
          "required": ["role", "content"]
        }
      },
      "temperature": { "type": "number", "minimum": 0, "maximum": 2 },
      "max_tokens": { "type": "integer", "minimum": 1, "maximum": 128000 }
    },
    "required": ["messages"]
  },
  "output": {
    "type": "object",
    "properties": {
      "id": { "type": "string" },
      "choices": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "message": { "type": "object" },
            "finish_reason": { "type": "string" }
          }
        }
      },
      "usage": {
        "type": "object",
        "properties": {
          "prompt_tokens": { "type": "integer" },
          "completion_tokens": { "type": "integer" },
          "total_tokens": { "type": "integer" }
        }
      }
    }
  },
  "metadata": {
    "contextWindow": 128000,
    "capabilities": ["chat", "function_calling", "vision", "json_mode"],
    "pricing": { "input": 0.01, "output": 0.03 }
  }
}'

echo "   MODEL_SCHEMA Content:"
echo "$MODEL_SCHEMA_CONTENT" | jq '.'
echo ""

MODEL_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts" \
  -H "Content-Type: application/json" \
  -d "{
    \"artifactId\": \"gpt-4-turbo-schema\",
    \"artifactType\": \"MODEL_SCHEMA\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$MODEL_SCHEMA_CONTENT" | jq -Rs .),
        \"contentType\": \"application/json\"
      }
    }
  }")

echo "   Response:"
echo "$MODEL_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 3: Create a PROMPT_TEMPLATE artifact (Summarization)
echo "3. Creating PROMPT_TEMPLATE artifact (Summarization)..."
PROMPT_TEMPLATE_CONTENT='$schema: https://apicur.io/schemas/prompt-template/v1
templateId: summarization-v1
name: Document Summarization
description: Summarizes documents with configurable length and style
version: "1.0"

template: |
  You are a helpful assistant that summarizes documents.

  Style: {{style}}
  Maximum length: {{max_words}} words

  Document to summarize:
  {{document}}

  Please provide a {{style}} summary in no more than {{max_words}} words.

variables:
  style:
    type: string
    enum: [concise, detailed, bullet-points]
    default: concise
    description: The style of summary to generate
  max_words:
    type: integer
    minimum: 50
    maximum: 1000
    default: 200
    description: Maximum number of words in the summary
  document:
    type: string
    required: true
    description: The document content to summarize

outputSchema:
  type: object
  properties:
    summary:
      type: string
    wordCount:
      type: integer

metadata:
  author: team-ai
  tags: [summarization, documents, nlp]
  recommendedModels: [gpt-4-turbo, claude-3-opus]
  estimatedTokens:
    input: 150
    variableOverhead: 2.5'

echo "   PROMPT_TEMPLATE Content:"
echo "$PROMPT_TEMPLATE_CONTENT"
echo ""

PROMPT_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts" \
  -H "Content-Type: application/json" \
  -d "{
    \"artifactId\": \"summarization-prompt\",
    \"artifactType\": \"PROMPT_TEMPLATE\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$PROMPT_TEMPLATE_CONTENT" | jq -Rs .),
        \"contentType\": \"application/x-yaml\"
      }
    }
  }")

echo "   Response:"
echo "$PROMPT_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 4: Test content auto-detection (MODEL_SCHEMA)
echo "4. Testing content auto-detection (MODEL_SCHEMA)..."
AUTO_MODEL_CONTENT='{
  "modelId": "claude-3-opus",
  "provider": "anthropic",
  "input": {
    "type": "object",
    "properties": {
      "messages": { "type": "array" },
      "max_tokens": { "type": "integer" }
    },
    "required": ["messages", "max_tokens"]
  },
  "output": {
    "type": "object",
    "properties": {
      "content": { "type": "array" },
      "stop_reason": { "type": "string" }
    }
  }
}'

AUTO_MODEL_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts" \
  -H "Content-Type: application/json" \
  -d "{
    \"artifactId\": \"claude-3-opus-schema-auto\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$AUTO_MODEL_CONTENT" | jq -Rs .),
        \"contentType\": \"application/json\"
      }
    }
  }")

echo "   Response (note the artifactType field - should be MODEL_SCHEMA):"
echo "$AUTO_MODEL_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 5: Test content auto-detection (PROMPT_TEMPLATE)
echo "5. Testing content auto-detection (PROMPT_TEMPLATE)..."
AUTO_PROMPT_CONTENT='templateId: qa-assistant
name: Q&A Assistant
template: |
  Answer the following question based on the context provided.

  Context: {{context}}
  Question: {{question}}

  Provide a helpful and accurate answer.

variables:
  context:
    type: string
    required: true
  question:
    type: string
    required: true'

AUTO_PROMPT_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts" \
  -H "Content-Type: application/json" \
  -d "{
    \"artifactId\": \"qa-prompt-auto\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$AUTO_PROMPT_CONTENT" | jq -Rs .),
        \"contentType\": \"application/x-yaml\"
      }
    }
  }")

echo "   Response (note the artifactType field - should be PROMPT_TEMPLATE):"
echo "$AUTO_PROMPT_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 6: Enable compatibility rule and test MODEL_SCHEMA compatibility
echo "6. Testing MODEL_SCHEMA backward compatibility..."
echo "   Enabling BACKWARD compatibility rule..."

curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/rules" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleType": "COMPATIBILITY",
    "config": "BACKWARD"
  }' > /dev/null

echo "   Creating compatible new version (adding optional field)..."
COMPATIBLE_MODEL='{
  "$schema": "https://apicur.io/schemas/model-schema/v1",
  "modelId": "gpt-4-turbo",
  "provider": "openai",
  "version": "2024-04",
  "input": {
    "type": "object",
    "properties": {
      "messages": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "role": { "type": "string", "enum": ["system", "user", "assistant"] },
            "content": { "type": "string" }
          },
          "required": ["role", "content"]
        }
      },
      "temperature": { "type": "number", "minimum": 0, "maximum": 2 },
      "max_tokens": { "type": "integer", "minimum": 1, "maximum": 128000 },
      "response_format": { "type": "object", "description": "New optional field for structured outputs" }
    },
    "required": ["messages"]
  },
  "output": {
    "type": "object",
    "properties": {
      "id": { "type": "string" },
      "choices": { "type": "array" },
      "usage": { "type": "object" }
    }
  },
  "metadata": {
    "contextWindow": 128000,
    "capabilities": ["chat", "function_calling", "vision", "json_mode", "structured_outputs"]
  }
}'

COMPATIBLE_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts/gpt-4-turbo-schema/versions" \
  -H "Content-Type: application/json" \
  -d "{
    \"version\": \"2.0.0\",
    \"content\": {
      \"content\": $(echo "$COMPATIBLE_MODEL" | jq -Rs .),
      \"contentType\": \"application/json\"
    }
  }")

echo "   Response (should succeed):"
echo "$COMPATIBLE_RESPONSE" | jq '.'
echo ""

echo "   Attempting incompatible change (adding required input field)..."
INCOMPATIBLE_MODEL='{
  "$schema": "https://apicur.io/schemas/model-schema/v1",
  "modelId": "gpt-4-turbo",
  "provider": "openai",
  "version": "2024-06",
  "input": {
    "type": "object",
    "properties": {
      "messages": { "type": "array" },
      "temperature": { "type": "number" },
      "max_tokens": { "type": "integer" },
      "new_required_field": { "type": "string" }
    },
    "required": ["messages", "new_required_field"]
  },
  "output": {
    "type": "object",
    "properties": {
      "id": { "type": "string" },
      "choices": { "type": "array" }
    }
  }
}'

INCOMPATIBLE_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts/gpt-4-turbo-schema/versions" \
  -H "Content-Type: application/json" \
  -d "{
    \"version\": \"3.0.0\",
    \"content\": {
      \"content\": $(echo "$INCOMPATIBLE_MODEL" | jq -Rs .),
      \"contentType\": \"application/json\"
    }
  }")

echo "   Response (should show compatibility error):"
echo "$INCOMPATIBLE_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 7: Test PROMPT_TEMPLATE validation
echo "7. Testing PROMPT_TEMPLATE validation..."
echo "   Enabling FULL validity rule..."

curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/rules" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleType": "VALIDITY",
    "config": "FULL"
  }' > /dev/null 2>&1

echo "   Attempting to create prompt with undefined variable..."
INVALID_PROMPT='templateId: invalid-prompt
name: Invalid Prompt
template: |
  Hello {{name}}, please help with {{undefined_variable}}.

variables:
  name:
    type: string
    required: true'

INVALID_RESPONSE=$(curl -s -X POST "$REGISTRY_URL/groups/$GROUP_ID/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactType: PROMPT_TEMPLATE" \
  -d "{
    \"artifactId\": \"invalid-prompt\",
    \"artifactType\": \"PROMPT_TEMPLATE\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$INVALID_PROMPT" | jq -Rs .),
        \"contentType\": \"application/x-yaml\"
      }
    }
  }")

echo "   Response (should show validation error about undefined variable):"
echo "$INVALID_RESPONSE" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 8: List all artifacts
echo "8. Listing all artifacts in the group..."
echo "   Command: curl -s $REGISTRY_URL/groups/$GROUP_ID/artifacts"
curl -s "$REGISTRY_URL/groups/$GROUP_ID/artifacts" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 9: Get artifact versions
echo "9. Listing versions of gpt-4-turbo-schema..."
curl -s "$REGISTRY_URL/groups/$GROUP_ID/artifacts/gpt-4-turbo-schema/versions" | jq '.'
echo ""
read -p "Press Enter to continue..."
echo ""

# Step 10: Create support chat prompts for the Quarkus demo
echo "10. Creating support chat prompts for Quarkus demo..."
echo ""
echo "   Creating apicurio-support-system-prompt (PROMPT_TEMPLATE)..."

SUPPORT_SYSTEM_PROMPT='$schema: https://apicur.io/schemas/prompt-template/v1
templateId: apicurio-support-system-prompt
name: Apicurio Registry Support Assistant - System Prompt
description: System prompt for the Apicurio Registry support assistant chatbot
version: "1.0"

template: |
  You are a helpful support assistant for Apicurio Registry, an open-source schema and API registry.

  ## Your Role
  - Answer questions about Apicurio Registry features, configuration, deployment, and usage
  - Guide users through common tasks like installing, configuring, and using the registry
  - Help troubleshoot common issues
  - Explain concepts like schema validation, artifact types, versioning, and compatibility rules

  ## Guidelines
  - Be concise but thorough in your answers
  - If you are not sure about something, say so rather than making up information
  - When relevant, mention specific configuration properties, API endpoints, or CLI commands
  - Use markdown formatting for code snippets, lists, and emphasis

  ## Key Topics You Can Help With
  - Installation (Docker, Kubernetes, standalone)
  - Configuration (storage backends, security, logging)
  - Artifact types: {{supported_artifact_types}}
  - Schema validation and compatibility rules
  - REST API usage (v3)
  - Client SDKs (Java, Python)
  - Kafka integration (SerDes)
  - Security and authentication (OIDC, RBAC)
  - High availability and scaling

variables:
  supported_artifact_types:
    type: string
    default: "AVRO, PROTOBUF, JSON, OPENAPI, ASYNCAPI, GRAPHQL, KCONNECT, WSDL, XSD, XML, PROMPT_TEMPLATE, MODEL_SCHEMA"
    description: Comma-separated list of supported artifact types
  additional_context:
    type: string
    required: false
    description: Optional additional context to include in the system prompt

metadata:
  author: apicurio-team
  createdAt: "2025-01-21"
  tags: [support, chatbot, system-prompt, apicurio-registry]
  recommendedModels: [llama3.2, gpt-4-turbo, claude-3-sonnet]'

curl -s -X POST "$REGISTRY_URL/groups/default/artifacts" \
  -H "Content-Type: application/json" \
  -d "{
    \"artifactId\": \"apicurio-support-system-prompt\",
    \"artifactType\": \"PROMPT_TEMPLATE\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$SUPPORT_SYSTEM_PROMPT" | jq -Rs .),
        \"contentType\": \"application/x-yaml\"
      }
    }
  }" | jq '.'

echo ""
echo "   Creating apicurio-support-chat-prompt (PROMPT_TEMPLATE)..."

SUPPORT_CHAT_PROMPT='$schema: https://apicur.io/schemas/prompt-template/v1
templateId: apicurio-support-chat-prompt
name: Apicurio Registry Support Chat - User Message Prompt
description: Template for formatting user questions in the support chat
version: "1.0"

template: |
  {{system_prompt}}

  ## Current Question
  User: {{question}}

  ## Instructions
  Please provide a helpful, accurate answer based on your knowledge of Apicurio Registry.
  Include relevant code examples or configuration snippets where appropriate.

  Assistant:

variables:
  system_prompt:
    type: string
    required: true
    description: The rendered system prompt from apicurio-support-system-prompt
  question:
    type: string
    required: true
    description: The current question from the user
  conversation_history:
    type: string
    required: false
    description: Previous conversation turns for context
  include_examples:
    type: boolean
    default: true
    description: Whether to include code examples in responses

metadata:
  author: apicurio-team
  createdAt: "2025-01-21"
  tags: [support, chatbot, user-prompt, conversation]
  recommendedModels: [llama3.2, gpt-4-turbo, claude-3-sonnet]'

curl -s -X POST "$REGISTRY_URL/groups/default/artifacts" \
  -H "Content-Type: application/json" \
  -d "{
    \"artifactId\": \"apicurio-support-chat-prompt\",
    \"artifactType\": \"PROMPT_TEMPLATE\",
    \"firstVersion\": {
      \"version\": \"1.0.0\",
      \"content\": {
        \"content\": $(echo "$SUPPORT_CHAT_PROMPT" | jq -Rs .),
        \"contentType\": \"application/x-yaml\"
      }
    }
  }" | jq '.'

echo ""
echo "   Support chat prompts created in 'default' group."
echo ""
read -p "Press Enter to continue..."
echo ""

# Summary
echo "=================================================="
echo "Demo Complete!"
echo "=================================================="
echo ""
echo "This demo demonstrated:"
echo "  - Custom artifact type registration (MODEL_SCHEMA, PROMPT_TEMPLATE)"
echo "  - Artifact creation with explicit type"
echo "  - Content auto-detection (ContentAccepter)"
echo "  - MODEL_SCHEMA backward compatibility checking"
echo "  - PROMPT_TEMPLATE variable validation"
echo "  - Version management"
echo "  - Support chat prompts"
echo ""
echo "You can explore the registry using:"
echo "  - Web UI:  http://localhost:8888"
echo "  - API:     http://localhost:8080/apis/registry/v3"
echo ""
echo "For a complete support chat application with RAG and Kubernetes deployment,"
echo "see: https://github.com/carlesarnal/apicurio-registry-support-chat"
echo ""
