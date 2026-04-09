# Apicurio Registry Support Chat

AI-powered support assistant for [Apicurio Registry](https://www.apicur.io/registry/) with RAG (Retrieval-Augmented Generation) and LLM integration.

## Features

| Feature | Description |
|---------|-------------|
| **PROMPT_TEMPLATE** | System and chat prompts stored in Apicurio Registry, rendered via /render endpoint |
| **RAG** | Automatic ingestion of Apicurio Registry documentation from web |
| **In-process Embeddings** | ONNX-based embeddings run inside the JVM — no external embedding service needed |
| **Conversation Memory** | Session-based chat with context preservation |
| **Embeddable Widget** | Chat widget that can be embedded on any website via a single `<script>` tag |
| **Kubernetes Ready** | Health checks, ConfigMaps, and deployment manifests included |

## Architecture

```
┌─────────────────────┐        ┌──────────────────────┐        ┌──────────────┐
│   www.apicur.io     │        │  Support Chat        │        │ Google AI    │
│   (GitHub Pages)    │  REST  │  (Quarkus)           │  API   │ Gemini       │
│ ┌─────────────────┐ │───────▶│  ├─ RAG (in-process) │───────▶│ (LLM)       │
│ │ chat-widget.js  │ │        │  └─ ONNX embeddings  │        └──────────────┘
│ └─────────────────┘ │        └──────────┬───────────┘
└─────────────────────┘                   │
                              ┌───────────┼───────────┐
                              ▼           ▼           ▼
                      ┌───────────┐ ┌──────────┐ ┌──────────┐
                      │ Registry  │ │   RAG    │ │   Docs   │
                      │ (Prompts) │ │ (ONNX)   │ │  (Web)   │
                      └───────────┘ └──────────┘ └──────────┘
```

## Prerequisites

- **Java 21+** and **Maven 3.8+**
- A **Google AI API key** ([get one free](https://aistudio.google.com/apikey))
- **Docker** (for containerized deployment)

## Quick Start

### 1. Start Apicurio Registry

```bash
docker run -d --name apicurio-registry -p 8080:8080 quay.io/apicurio/apicurio-registry:3.2.0
```

### 2. Create Prompt Templates

```bash
./scripts/create-prompts.sh
```

### 3. Run the Application

```bash
export GOOGLE_AI_GEMINI_API_KEY=your-api-key
mvn quarkus:dev -Dquarkus.http.port=8081
```

Open http://localhost:8081 in your browser.

## Docker Compose

Run the complete stack with Docker Compose:

```bash
# Set your API key
export GOOGLE_AI_GEMINI_API_KEY=your-api-key

# Build the application first
mvn package -DskipTests

# Start all services
docker compose up -d
```

Services:
- **Support Chat**: http://localhost:8081
- **Apicurio Registry**: http://localhost:8080

## Deploying to Render.com (Free)

### 1. Fork the repository

### 2. Create a Render Blueprint

Go to [Render Dashboard](https://dashboard.render.com/) and create a new **Blueprint** pointing to the `support-chat/render.yaml` file.

### 3. Set the API key

In the Render dashboard, set the `GOOGLE_AI_GEMINI_API_KEY` environment variable for the `apicurio-support-chat` service.

### 4. Create prompt templates

Once deployed, run the prompt creation script against the deployed registry:

```bash
REGISTRY_URL=https://apicurio-registry.onrender.com/apis/registry/v3 ./scripts/create-prompts.sh
```

## Embedding the Chat Widget

Add a single script tag to any website:

```html
<script src="https://apicurio-support-chat.onrender.com/chat-widget.js"></script>
```

This renders a floating chat button that opens an AI-powered support panel. The widget:
- Creates sessions automatically on first use
- Preserves conversation history across page navigations (via the session)
- Is fully self-contained (CSS + JS in one file)
- Is responsive and works on mobile

## Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `GOOGLE_AI_GEMINI_API_KEY` | (required) | Google AI API key |
| `GEMINI_MODEL` | `gemini-2.0-flash` | Gemini chat model ID |
| `REGISTRY_URL` | `http://localhost:8080` | Apicurio Registry URL |
| `REGISTRY_GROUP` | `default` | Registry group for prompts |
| `CORS_ORIGINS` | `*` | Allowed CORS origins |
| `HTTP_PORT` | `8080` | Application HTTP port |

## API Endpoints

### Chat

```bash
# Create a session
curl -X POST http://localhost:8081/support/session

# Chat with session (preserves history)
curl -X POST http://localhost:8081/support/chat/{sessionId} \
  -H "Content-Type: application/json" \
  -d '{"message": "How do I install Apicurio Registry?"}'

# Quick chat (stateless)
curl -X POST http://localhost:8081/support/ask \
  -H "Content-Type: application/json" \
  -d '{"message": "What artifact types are supported?"}'
```

### Health & Status

```bash
# Health check
curl http://localhost:8081/support/health

# RAG ingestion status
curl http://localhost:8081/support/rag/status
```

### Prompt Templates

```bash
# Get raw system prompt template
curl http://localhost:8081/support/prompts/system

# Preview rendered prompt (without calling LLM)
curl -X POST http://localhost:8081/support/prompts/preview \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I configure storage?"}'
```

## Project Structure

```
support-chat/
├── src/main/java/io/apicurio/registry/support/
│   ├── ApicurioSupportService.java    # Core chat service with RAG and /render endpoint
│   ├── SupportAiService.java          # LangChain4j AI service interface
│   ├── DocumentIngestionService.java  # Web docs ingestion at startup
│   └── SupportChatResource.java       # REST API endpoints
├── src/main/resources/
│   ├── META-INF/resources/
│   │   ├── index.html                 # Standalone web chat UI
│   │   └── chat-widget.js             # Embeddable chat widget
│   └── application.properties         # Configuration
├── src/main/docker/
│   └── Dockerfile.jvm                 # Container image
├── scripts/
│   └── create-prompts.sh              # Script to create prompt templates in Registry
├── k8s/
│   ├── deployment.yaml                # Kubernetes Deployment + Service
│   └── configmap.yaml                 # Environment configuration
├── render.yaml                        # Render.com deployment blueprint
├── render-registry.Dockerfile         # Dockerfile for Registry on Render
├── docker-compose.yaml                # Local development stack
├── pom.xml                            # Maven build configuration
└── README.md
```

## Using with Ollama (Local Development)

To use Ollama instead of Google AI Gemini for local development:

1. Replace the dependency in `pom.xml`:
   ```xml
   <dependency>
       <groupId>io.quarkiverse.langchain4j</groupId>
       <artifactId>quarkus-langchain4j-ollama</artifactId>
       <version>${quarkus-langchain4j.version}</version>
   </dependency>
   ```

2. Configure in `application.properties`:
   ```properties
   quarkus.langchain4j.chat-model.provider=ollama
   quarkus.langchain4j.ollama.base-url=http://localhost:11434
   quarkus.langchain4j.ollama.chat-model.model-id=llama3.2
   ```

3. Pull models: `ollama pull llama3.2`

## License

Apache License 2.0

## Links

- [Apicurio Registry](https://www.apicur.io/registry/)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Quarkus LangChain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/)
- [Google AI Studio](https://aistudio.google.com/)
