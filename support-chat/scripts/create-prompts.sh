#!/bin/bash

# Create prompt templates in Apicurio Registry

REGISTRY_URL="${REGISTRY_URL:-http://localhost:8080/apis/registry/v3}"

echo "Creating prompt templates in Apicurio Registry..."
echo "Registry URL: $REGISTRY_URL"
echo ""

# System Prompt
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
  tags: [support, chatbot, system-prompt, apicurio-registry]
  recommendedModels: [llama3.2, gpt-4-turbo, claude-3-sonnet]'

echo "Creating apicurio-support-system-prompt..."
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
  }" | jq -r '.artifact.artifactId // .message // .'

# Chat Prompt
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
  tags: [support, chatbot, user-prompt, conversation]
  recommendedModels: [llama3.2, gpt-4-turbo, claude-3-sonnet]'

echo "Creating apicurio-support-chat-prompt..."
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
  }" | jq -r '.artifact.artifactId // .message // .'

echo ""
echo "Done! Prompts created in Apicurio Registry."
echo "View them at: ${REGISTRY_URL%/apis/registry/v3}/ui"
