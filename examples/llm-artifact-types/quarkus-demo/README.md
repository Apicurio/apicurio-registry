# Quarkus LangChain4j Demo

This demo application shows how to integrate Apicurio Registry with Quarkus and LangChain4j for managing versioned prompts in LLM applications.

## Prerequisites

1. **Java 17+** and **Maven 3.8+**

2. **Apicurio Registry running** on port 8080:
   ```bash
   # Option 1: Using Docker
   cd ..
   docker compose up -d
   ./demo.sh  # Creates sample prompts

   # Option 2: Running locally (from registry root)
   cd ../../..
   mvn quarkus:dev -pl app -Dquarkus.http.port=8080
   ```

3. **Ollama** (default, free local LLM) or **OpenAI API key**

## LLM Provider Setup

### Option 1: Ollama (Recommended - Free, Local)

Install and run Ollama with a model:

```bash
# macOS
brew install ollama
brew services start ollama
ollama pull llama3.2

# Linux
curl -fsSL https://ollama.com/install.sh | sh
ollama serve &
ollama pull llama3.2
```

The demo is pre-configured for Ollama. No additional setup needed.

### Option 2: OpenAI (Paid API)

1. Update `pom.xml` to use OpenAI:
   ```xml
   <dependency>
       <groupId>io.quarkiverse.langchain4j</groupId>
       <artifactId>quarkus-langchain4j-openai</artifactId>
       <version>1.5.0</version>
   </dependency>
   ```

2. Update `application.properties`:
   ```properties
   quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
   quarkus.langchain4j.openai.chat-model.model-name=gpt-4o
   ```

3. Set your API key:
   ```bash
   export OPENAI_API_KEY=your-key-here
   ```

## Creating Sample Prompts

Before running the demo, create the prompt templates in the registry:

```bash
# Create summarization prompt
curl -X POST "http://localhost:8080/apis/registry/v3/groups/default/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "artifactId": "summarization-v1",
    "artifactType": "PROMPT_TEMPLATE",
    "firstVersion": {
      "content": {
        "content": "{\"template\": \"Please summarize the following document in a {{style}} manner, keeping it under {{max_words}} words:\\n\\n{{document}}\\n\\nProvide a clear and informative summary.\", \"input_variables\": [\"document\", \"style\", \"max_words\"]}",
        "contentType": "application/json"
      }
    }
  }'

# Create QA prompt
curl -X POST "http://localhost:8080/apis/registry/v3/groups/default/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "artifactId": "qa-prompt",
    "artifactType": "PROMPT_TEMPLATE",
    "firstVersion": {
      "content": {
        "content": "{\"template\": \"Based on the following context, please answer the question.\\n\\nContext: {{context}}\\n\\nQuestion: {{question}}\\n\\nAnswer:\", \"input_variables\": [\"context\", \"question\"]}",
        "contentType": "application/json"
      }
    }
  }'
```

## Running the Demo

### Development Mode

```bash
mvn quarkus:dev
```

The application starts on port 8081 (to avoid conflict with registry on 8080).

### Production Mode

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## API Endpoints

### Health Check

```bash
curl http://localhost:8081/chat/health
# Response: {"registry":"connected","status":"UP"}
```

### Summarize Document

Uses the `summarization-v1` prompt from the registry:

```bash
curl -X POST http://localhost:8081/chat/summarize \
  -H "Content-Type: application/json" \
  -d '{
    "document": "Apicurio Registry is an open-source schema registry that provides storage and management of API artifacts including OpenAPI, AsyncAPI, GraphQL, Protobuf, Avro, and JSON schemas.",
    "style": "concise",
    "maxWords": 50
  }'
```

### Summarize with Specific Version

```bash
curl -X POST http://localhost:8081/chat/summarize/1 \
  -H "Content-Type: application/json" \
  -d '{
    "document": "...",
    "style": "bullet-points"
  }'
```

### Ask a Question

Uses the `qa-prompt` from the registry:

```bash
curl "http://localhost:8081/chat/ask?question=What%20is%20Apicurio%20Registry?"
# Response: {"question":"What is Apicurio Registry?","answer":"Apicurio Registry is a schema registry for managing API artifacts."}
```

### Preview Prompt (without LLM call)

Useful for debugging prompt rendering:

```bash
curl -X POST http://localhost:8081/chat/preview \
  -H "Content-Type: application/json" \
  -d '{
    "artifactId": "summarization-v1",
    "version": null,
    "variables": {
      "document": "Test document",
      "style": "concise",
      "max_words": 50
    }
  }'
```

### Clear Prompt Cache

```bash
curl -X POST http://localhost:8081/chat/cache/clear
```

## Code Structure

```
src/main/java/io/apicurio/registry/demo/
└── ChatResource.java      # REST endpoints demonstrating registry integration
```

## Key Concepts

### Injecting the Prompt Registry

```java
@Inject
ApicurioPromptRegistry promptRegistry;

@Inject
ChatModel chatModel;  // Automatically configured by quarkus-langchain4j
```

### Fetching and Using Prompts

```java
// Get latest version
ApicurioPromptTemplate template = promptRegistry.getPrompt("summarization-v1");

// Get specific version
ApicurioPromptTemplate template = promptRegistry.getPrompt("summarization-v1", "1");

// Apply variables
Prompt prompt = template.apply(Map.of(
    "document", document,
    "style", "concise",
    "max_words", 100
));

// Send to LLM
String response = chatModel.chat(prompt.text());
```

### Configuration

In `application.properties`:

```properties
# Registry connection
apicurio.registry.url=http://localhost:8080
apicurio.registry.default-group=default

# Ollama configuration (default - free, local)
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=llama3.2
quarkus.langchain4j.ollama.timeout=120s

# OR OpenAI configuration (requires API key)
# quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
# quarkus.langchain4j.openai.chat-model.model-name=gpt-4o
```

## Using Different LLM Providers

### Anthropic Claude

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-anthropic</artifactId>
    <version>1.5.0</version>
</dependency>
```

```properties
quarkus.langchain4j.anthropic.api-key=${ANTHROPIC_API_KEY}
quarkus.langchain4j.anthropic.chat-model.model-name=claude-3-5-sonnet-20241022
```

### Azure OpenAI

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-azure-openai</artifactId>
    <version>1.5.0</version>
</dependency>
```

### Mistral AI

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-mistral-ai</artifactId>
    <version>1.5.0</version>
</dependency>
```

## Troubleshooting

### "Connection refused" to registry

Make sure Apicurio Registry is running:
```bash
curl http://localhost:8080/apis/registry/v3/system/info
```

### "Prompt not found" error

Create the sample prompts (see "Creating Sample Prompts" section above).

### Ollama errors

Make sure Ollama is running and the model is pulled:
```bash
ollama list          # Should show llama3.2
curl http://localhost:11434/api/tags  # Should return models
```

### OpenAI API errors

- Check your API key is valid
- Check your API quota at https://platform.openai.com/account/billing
- For testing without an LLM, use the `/preview` endpoint
