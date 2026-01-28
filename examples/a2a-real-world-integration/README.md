# Real-World A2A Integration Example

This example demonstrates **actual working A2A protocol integration** with Apicurio Registry. Unlike simulated examples, this runs real HTTP servers that implement the A2A protocol and communicate over the network.

## What Makes This Real

| Component | Description |
|-----------|-------------|
| **Mock Agents** | Real HTTP servers on ports 9001-9003 implementing A2A endpoints |
| **A2A Discovery** | Agents expose `/.well-known/agent.json` for capability discovery |
| **A2A Tasks** | Agents accept `POST /a2a` with JSON-RPC for task execution |
| **Orchestrator** | Makes real HTTP requests to discover and invoke agents |
| **Registry Integration** | Agents registered and discovered via `/.well-known/agents` |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Real A2A Protocol Flow                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐                                          │
│  │   Orchestrator   │                                          │
│  └────────┬─────────┘                                          │
│           │                                                     │
│           │ 1. GET /.well-known/agents                         │
│           ▼                                                     │
│  ┌──────────────────┐                                          │
│  │ Apicurio Registry│ ◄─── Agent cards stored here             │
│  └────────┬─────────┘                                          │
│           │                                                     │
│           │ 2. Returns agent URLs                              │
│           ▼                                                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐│
│  │ Sentiment Agent  │  │ Summarizer Agent │  │ Translate Agent││
│  │   :9001          │  │    :9002         │  │    :9003       ││
│  └────────┬─────────┘  └────────┬─────────┘  └───────┬────────┘│
│           │                     │                     │         │
│           │ 3. POST /a2a (JSON-RPC task)              │         │
│           │                     │                     │         │
│           ▼                     ▼                     ▼         │
│      ┌─────────────────────────────────────────────────┐       │
│      │          Real HTTP Responses with Results       │       │
│      └─────────────────────────────────────────────────┘       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker and Docker Compose

### Running the Example

1. **Start the Registry:**
   ```bash
   docker-compose up -d
   ```

2. **Wait for healthy status:**
   ```bash
   docker-compose logs -f apicurio-registry
   # Wait for "Installed features" message
   ```

3. **Run the demo:**
   ```bash
   mvn clean compile exec:java
   ```

4. **Observe real A2A communication:**
   - Phase 1: Mock agents start on ports 9001, 9002, 9003
   - Phase 2: Agents verified via `GET /.well-known/agent.json`
   - Phase 3: Agents registered in Apicurio Registry
   - Phase 4: Agents discovered via `GET /.well-known/agents`
   - Phase 5: Multi-agent workflow executed via `POST /a2a`

## Demo Agents

| Agent | Port | Skills | Description |
|-------|------|--------|-------------|
| **Sentiment Analysis** | 9001 | sentiment-analysis, emotion-detection | Analyzes text sentiment (positive/negative/neutral) |
| **Text Summarization** | 9002 | text-summarization, key-extraction | Summarizes long text into concise summaries |
| **Translation** | 9003 | translation, language-detection | Translates text between languages |

## A2A Protocol Endpoints

### Agent Discovery (per agent)
```bash
# Get agent's capability card
curl http://localhost:9001/.well-known/agent.json
```

Response:
```json
{
  "name": "Sentiment Analysis Agent",
  "description": "Analyzes text sentiment...",
  "version": "1.0.0",
  "url": "http://localhost:9001",
  "skills": [
    {"id": "sentiment-analysis", "name": "sentiment-analysis", ...}
  ],
  "capabilities": {"streaming": false, "pushNotifications": false}
}
```

### Task Execution (A2A JSON-RPC)
```bash
curl -X POST http://localhost:9001/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tasks/send",
    "params": {
      "id": "task-1",
      "sessionId": "session-1",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "I love this product!"}]
      }
    }
  }'
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "id": "...",
    "status": {"state": "completed"},
    "artifacts": [{
      "parts": [{"type": "text", "text": "{\"sentiment\": \"POSITIVE\", ...}"}]
    }]
  }
}
```

### Registry Discovery
```bash
# Discover all agents from registry
curl http://localhost:8080/.well-known/agents

# Get specific agent
curl http://localhost:8080/.well-known/agents/demo.agents/sentiment-agent
```

## Code Structure

```
src/main/java/io/apicurio/registry/examples/a2a/realworld/
├── RealA2ADemo.java              # Main demo application
├── agents/
│   └── MockAgentServer.java      # HTTP server implementing A2A protocol
└── orchestrator/
    └── A2AOrchestrator.java      # Agent discovery and task delegation
```

### Key Classes

**MockAgentServer.java**
- Creates HTTP server using `com.sun.net.httpserver`
- Implements `/.well-known/agent.json` endpoint
- Implements `/a2a` JSON-RPC endpoint for task execution
- Configurable with custom task handlers

**A2AOrchestrator.java**
- `discoverAgents()` - Queries registry's `/.well-known/agents`
- `fetchAgentCardDirect()` - Gets agent card from agent's own endpoint
- `sendTask()` - Sends A2A JSON-RPC task to agent
- `executeWorkflow()` - Runs multi-step workflow across agents

**RealA2ADemo.java**
- Starts mock agents as real HTTP servers
- Registers agents in Apicurio Registry
- Demonstrates discovery and task execution
- Runs a customer complaint processing workflow

## Sample Workflow Output

```
================================================================================
Executing Workflow: Customer Complaint Processing Pipeline
================================================================================

[Step 1/3] Analyze customer sentiment
  Agent: http://localhost:9001
  Task: I am so frustrated with your service!...
  [Orchestrator] Sending task to: http://localhost:9001/a2a
  [Sentiment Analysis Agent] Received A2A request: {"jsonrpc":"2.0",...}
  [Sentiment Analysis Agent] Processed task: I am so frustrated...
  [Orchestrator] Response status: 200
  Result: {"sentiment": "NEGATIVE", "confidence": 0.84, ...}
  Duration: 5ms

[Step 2/3] Summarize the customer complaint
  Agent: http://localhost:9002
  ...
  Result: {"summary": "I am so frustrated with your service...", ...}
  Duration: 3ms

[Step 3/3] Process text through translation agent
  Agent: http://localhost:9003
  ...
  Result: {"translated_text": "...", ...}
  Duration: 3ms

================================================================================
Workflow Complete: 3/3 steps succeeded
================================================================================

Total Duration: 11ms
Success Rate: 3/3
```

## Extending the Example

### Adding a New Agent

```java
MockAgentServer myAgent = new MockAgentServer(
    9004,                           // Port
    "My Custom Agent",              // Name
    "Description of what it does",  // Description
    new String[]{"skill-1", "skill-2"},  // Skills
    (message) -> {
        // Your processing logic here
        return "{\"result\": \"processed\"}";
    }
);
myAgent.start();
```

### Connecting to Real AI APIs

Replace the mock handler with real API calls:

```java
MockAgentServer openaiAgent = new MockAgentServer(
    9005,
    "OpenAI GPT Agent",
    "Powered by GPT-4",
    new String[]{"chat", "analysis"},
    (message) -> {
        // Call OpenAI API
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Authorization", "Bearer " + OPENAI_API_KEY)
            .POST(HttpRequest.BodyPublishers.ofString(buildPrompt(message)))
            .build();
        // ... handle response
    }
);
```

## A2A Protocol Compliance

This example implements core A2A protocol features:

| Feature | Status | Implementation |
|---------|--------|----------------|
| Agent Card (`/.well-known/agent.json`) | ✅ | `AgentCardHandler` |
| Task Submission (`tasks/send`) | ✅ | `A2ATaskHandler` |
| Task Status (`tasks/get`) | ✅ | `A2ATaskHandler` |
| JSON-RPC 2.0 | ✅ | Request/response format |
| Skills Discovery | ✅ | Skills array in agent card |
| Capabilities | ✅ | streaming, pushNotifications |

## Resources

- [A2A Protocol Specification](https://google.github.io/A2A/)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)

## License

Apache License 2.0
