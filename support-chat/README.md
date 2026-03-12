# Apicurio Registry Support Chat

AI-powered support assistant for [Apicurio Registry](https://www.apicur.io/registry/) with RAG (Retrieval-Augmented Generation) and LLM integration.

## Features

| Feature | Description |
|---------|-------------|
| **PROMPT_TEMPLATE** | System and chat prompts stored in Apicurio Registry, rendered via /render endpoint |
| **RAG** | Automatic ingestion of Apicurio Registry documentation from web |
| **Conversation Memory** | Session-based chat with context preservation |
| **Kubernetes Ready** | Health checks, ConfigMaps, and deployment manifests included |

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│   Web Browser   │────▶│  Support Chat    │────▶│   Ollama    │
│                 │     │  (Quarkus)       │     │   (LLM)     │
└─────────────────┘     └────────┬─────────┘     └─────────────┘
                                 │
                    ┌────────────┼────────────┐
                    ▼            ▼            ▼
            ┌───────────┐ ┌───────────┐ ┌───────────┐
            │ Registry  │ │    RAG    │ │   Docs    │
            │ (Prompts) │ │ (Vectors) │ │   (Web)   │
            └───────────┘ └───────────┘ └───────────┘
```

## Prerequisites

- **Java 17+** and **Maven 3.8+**
- **Docker** (for containerized deployment)
- **Kubernetes** (optional, for k8s deployment)

## Quick Start

### 1. Start Ollama (LLM)

```bash
# macOS
brew install ollama && brew services start ollama

# Linux
curl -fsSL https://ollama.com/install.sh | sh && ollama serve &

# Pull required models
ollama pull llama3.2
ollama pull nomic-embed-text
```

### 2. Start Apicurio Registry

```bash
docker run -d --name apicurio-registry -p 8080:8080 apicurio/apicurio-registry:3.0.6
```

### 3. Create Prompt Templates

```bash
./scripts/create-prompts.sh
```

### 4. Run the Application

```bash
mvn quarkus:dev
```

Open http://localhost:8081 in your browser.

## Docker Compose

Run the complete stack with Docker Compose:

```bash
# Build the application first
mvn package -DskipTests

# Start all services
docker compose up -d
```

Services:
- **Support Chat**: http://localhost:8081
- **Apicurio Registry**: http://localhost:8080
- **Ollama API**: http://localhost:11434

## Kubernetes Deployment

### Build and Push Container Image

```bash
# Build container image
mvn package -Dquarkus.container-image.build=true

# Push to registry
mvn package -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.registry=your-registry.io
```

### Deploy to Kubernetes

```bash
# Apply ConfigMap
kubectl apply -f k8s/configmap.yaml

# Deploy application
kubectl apply -f k8s/deployment.yaml
```

### Using Quarkus Kubernetes Extension

```bash
# Generate and apply Kubernetes manifests
mvn package -Dquarkus.kubernetes.deploy=true
```

## Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `REGISTRY_URL` | `http://localhost:8080` | Apicurio Registry URL |
| `REGISTRY_GROUP` | `default` | Registry group for prompts |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama API URL |
| `OLLAMA_MODEL` | `llama3.2` | Chat model ID |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Embedding model for RAG |
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

# Kubernetes health probes
curl http://localhost:8081/q/health/live
curl http://localhost:8081/q/health/ready
```

### Prompt Templates

```bash
# Get raw system prompt template
curl http://localhost:8081/support/prompts/system

# Get a specific prompt template
curl http://localhost:8081/support/prompts/{artifactId}

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
│   ├── DocumentIngestionService.java  # Web docs ingestion at startup
│   └── SupportChatResource.java       # REST API endpoints
├── src/main/resources/
│   ├── META-INF/resources/
│   │   └── index.html                 # Web chat UI
│   └── application.properties         # Configuration
├── src/main/docker/
│   └── Dockerfile.jvm                 # Container image
├── scripts/
│   └── create-prompts.sh              # Script to create prompt templates in Registry
├── k8s/
│   ├── deployment.yaml                # Kubernetes Deployment + Service
│   └── configmap.yaml                 # Environment configuration
├── docker-compose.yaml                # Local development stack
├── pom.xml                            # Maven build configuration
└── README.md
```

## Development

### Running in Dev Mode

```bash
mvn quarkus:dev
```

Features:
- Hot reload on code changes
- Dev UI at http://localhost:8081/q/dev/

### Building for Production

```bash
# JVM build
mvn package -DskipTests

# Native build (requires GraalVM)
mvn package -Pnative -DskipTests
```

### Running Tests

```bash
mvn test
```

## Using with OpenAI

To use OpenAI instead of Ollama:

1. Add dependency in `pom.xml`:
   ```xml
   <dependency>
       <groupId>io.quarkiverse.langchain4j</groupId>
       <artifactId>quarkus-langchain4j-openai</artifactId>
       <version>${quarkus-langchain4j.version}</version>
   </dependency>
   ```

2. Configure in `application.properties`:
   ```properties
   quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
   quarkus.langchain4j.openai.chat-model.model-name=gpt-4o
   ```

## License

Apache License 2.0

## Links

- [Apicurio Registry](https://www.apicur.io/registry/)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Quarkus LangChain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/)
- [Ollama](https://ollama.com/)
