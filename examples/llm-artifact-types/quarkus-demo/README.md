# Quarkus LangChain4j Demo

This demo application shows how to integrate Apicurio Registry with Quarkus and LangChain4j for managing versioned prompts in LLM applications.

## Prerequisites

1. **Apicurio Registry running** with sample data:
   ```bash
   cd ..
   docker compose up -d
   ./demo.sh  # Creates sample prompts
   ```

2. **OpenAI API key** (optional, for LLM calls):
   ```bash
   export OPENAI_API_KEY=your-key-here
   ```

3. **Java 17+** and **Maven 3.8+**

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
```

### Summarize Document

Uses the `summarization-v1` prompt from the registry:

```bash
curl -X POST http://localhost:8081/chat/summarize \
  -H "Content-Type: application/json" \
  -d '{
    "document": "Apicurio Registry is an open-source schema registry...",
    "style": "concise",
    "maxWords": 100
  }'
```

### Summarize with Specific Version

```bash
curl -X POST http://localhost:8081/chat/summarize/1.0 \
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
```

### Fetching and Using Prompts

```java
// Get latest version
PromptTemplate template = promptRegistry.getPrompt("summarization-v1");

// Get specific version
PromptTemplate template = promptRegistry.getPrompt("summarization-v1", "1.0");

// Apply variables
Prompt prompt = template.apply(Map.of(
    "document", document,
    "style", "concise"
));

// Send to LLM
String response = chatModel.generate(prompt.text());
```

### Configuration

In `application.properties`:

```properties
# Registry connection
apicurio.registry.url=http://localhost:8080
apicurio.registry.default-group=default

# LLM configuration
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
quarkus.langchain4j.openai.chat-model.model-name=gpt-4-turbo
```

## Extending the Demo

### Adding Custom AI Services

```java
@RegisterAiService
public interface SummarizationService {

    @SystemMessage("You are a helpful assistant.")
    @UserMessage("{renderedPrompt}")
    String summarize(String renderedPrompt);
}
```

### Using with Different LLM Providers

Change the LangChain4j dependency and configuration:

```xml
<!-- For Anthropic Claude -->
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-anthropic</artifactId>
</dependency>
```

```properties
quarkus.langchain4j.anthropic.api-key=${ANTHROPIC_API_KEY}
quarkus.langchain4j.anthropic.chat-model.model-name=claude-3-opus
```

## Troubleshooting

### "Connection refused" to registry

Make sure Apicurio Registry is running:
```bash
docker compose ps
curl http://localhost:8080/apis/registry/v3/system/info
```

### "Prompt not found" error

Make sure sample data is created:
```bash
cd ..
./demo.sh
```

### OpenAI API errors

- Check your API key is valid
- Check your API quota
- For testing without an LLM, use the `/preview` endpoint
