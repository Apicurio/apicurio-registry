# Quarkus LangChain4j Demo

This demo application demonstrates how to integrate Apicurio Registry with Quarkus and LangChain4j, showcasing the LLM artifact types (PROMPT_TEMPLATE, MODEL_SCHEMA).

## Features Demonstrated

| Feature | Description |
|---------|-------------|
| **PROMPT_TEMPLATE** | System and chat prompts stored in registry |
| **MODEL_SCHEMA** | Model search by capabilities, provider, context window |
| **Variable substitution** | Dynamic prompt rendering with `{{variable}}` syntax |
| **Versioned prompts** | Fetching specific prompt versions from registry |
| **Conversation memory** | Session-based chat with context preservation |

## Prerequisites

1. **Java 17+** and **Maven 3.8+**

2. **Apicurio Registry running** on port 8080:
   ```bash
   cd ..
   docker compose up -d
   ./demo.sh  # Creates sample prompts including support chat prompts
   ```

3. **Ollama** (default, free local LLM):
   ```bash
   # macOS
   brew install ollama && brew services start ollama && ollama pull llama3.2

   # Linux
   curl -fsSL https://ollama.com/install.sh | sh && ollama serve & && ollama pull llama3.2
   ```

## Running the Demo

```bash
mvn quarkus:dev
```

The application starts on port 8081 (to avoid conflict with registry on 8080).

## API Endpoints

### Health Check

```bash
curl http://localhost:8081/support/health
```

### Session Management

```bash
# Create a session
curl -X POST http://localhost:8081/support/session

# Get session info
curl http://localhost:8081/support/session/{sessionId}

# End session
curl -X DELETE http://localhost:8081/support/session/{sessionId}
```

### Chat (PROMPT_TEMPLATE demonstration)

The chat endpoints fetch prompts from the registry:
- `apicurio-support-system-prompt` - System instructions
- `apicurio-support-chat-prompt` - Chat message formatting

```bash
# Chat with session (maintains conversation history)
curl -X POST http://localhost:8081/support/chat/{sessionId} \
  -H "Content-Type: application/json" \
  -d '{"message": "How do I install Apicurio Registry using Docker?"}'

# Quick chat (stateless, no session)
curl -X POST http://localhost:8081/support/ask \
  -H "Content-Type: application/json" \
  -d '{"message": "What artifact types are supported?"}'

# Use specific prompt versions
curl -X POST http://localhost:8081/support/chat/{sessionId} \
  -H "Content-Type: application/json" \
  -d '{
    "message": "How do I configure PostgreSQL storage?",
    "systemPromptVersion": "1.0.0",
    "chatPromptVersion": "1.0.0"
  }'
```

### Prompt Templates

```bash
# Get the system prompt template
curl http://localhost:8081/support/prompts/system

# Get any prompt by artifact ID
curl http://localhost:8081/support/prompts/summarization-prompt

# Preview rendered prompt (without calling LLM)
curl -X POST http://localhost:8081/support/prompts/preview \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I install Apicurio Registry?"}'

# Clear prompt cache
curl -X POST http://localhost:8081/support/prompts/cache/clear
```

### Model Search (MODEL_SCHEMA demonstration)

```bash
# Search models by capability
curl "http://localhost:8081/support/models?capability=vision&provider=openai"

# Search with context window filter
curl "http://localhost:8081/support/models?minContextWindow=100000"

# Compare models
curl "http://localhost:8081/support/models/compare?model=gpt-4-turbo&model=claude-3-opus"
```

### Conversation History

```bash
curl http://localhost:8081/support/history/{sessionId}
```

## Code Structure

```
src/main/java/io/apicurio/registry/demo/
├── ApicurioSupportService.java   # Service using registry prompts
└── SupportChatResource.java      # REST endpoints

src/main/resources/
└── application.properties        # Configuration
```

## How It Works

### 1. Prompts from Registry

The service fetches PROMPT_TEMPLATE artifacts from the registry:

```java
@Inject
ApicurioPromptRegistry promptRegistry;

// Fetch system prompt
ApicurioPromptTemplate template = promptRegistry.getPrompt("apicurio-support-system-prompt");

// Render with variables
Prompt prompt = template.apply(Map.of(
    "supported_artifact_types", "AVRO, PROTOBUF, JSON..."
));
```

### 2. Variable Substitution

Prompts use `{{variable}}` syntax for dynamic content:

```yaml
template: |
  Artifact types: {{supported_artifact_types}}
  User question: {{question}}

variables:
  supported_artifact_types:
    type: string
    default: "AVRO, PROTOBUF..."
  question:
    type: string
    required: true
```

### 3. Conversation Memory

The service maintains conversation history per session:

```java
// Build conversation context
String history = buildConversationHistory(sessionId);

// Include in prompt
Prompt prompt = chatTemplate.apply(Map.of(
    "conversation_history", history,
    "question", question
));
```

## Configuration

```properties
# Registry connection
apicurio.registry.url=http://localhost:8080
apicurio.registry.default-group=default
apicurio.registry.cache-enabled=true

# Ollama (free, local)
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=llama3.2
quarkus.langchain4j.ollama.timeout=120s
```

## Using OpenAI Instead of Ollama

1. Update `pom.xml`:
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

## Troubleshooting

### "Prompt not found" error

Run the demo script to create the support chat prompts:
```bash
cd .. && ./demo.sh
```

### Ollama errors

```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# Check model is available
ollama list
```

### Registry connection issues

```bash
curl http://localhost:8080/apis/registry/v3/system/info
```
