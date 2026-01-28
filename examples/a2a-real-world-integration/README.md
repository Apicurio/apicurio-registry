# A2A Context Chaining Demo

This example demonstrates **context chaining** in multi-agent workflows - where each agent receives the accumulated outputs from all previous agents, creating a truly integrated pipeline.

## The Problem: Isolated Agents

In a typical multi-agent setup, each agent works in isolation:

```
Customer Message → Agent 1 → Output 1
Customer Message → Agent 2 → Output 2  (doesn't see Output 1!)
Customer Message → Agent 3 → Output 3  (doesn't see Output 1 or 2!)
```

This means agents can't build on each other's work.

## The Solution: Context Chaining

With context chaining, each agent receives all previous outputs:

```
Customer Message
       │
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    Context-Aware Orchestrator                             │
│                                                                           │
│  WorkflowContext:                                                         │
│  ├─ originalMessage: "Customer complaint..."                              │
│  ├─ agentOutputs: {sentiment: "...", analysis: "...", response: "..."}   │
│  └─ buildPrompt(template) → substitutes {{variables}} with context       │
└──────────────────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Step 1: Sentiment Agent                                                  │
│  Input:  {{original}}                                                     │
│  Output: {"sentiment": "very_negative", "urgency": "critical"}           │
│          → stored as context["sentiment"]                                 │
└──────────────────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Step 2: Issue Analyzer                                                   │
│  Input:  {{original}} + {{sentiment}}                                     │
│  Output: {"priority": "P1", "issue_type": "delayed_delivery"}            │
│          → stored as context["analysis"]                                  │
└──────────────────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Step 3: Response Generator                                               │
│  Input:  {{original}} + {{sentiment}} + {{analysis}}                      │
│  Output: {"response": "Dear customer, I sincerely apologize..."}         │
│          → stored as context["response"]                                  │
└──────────────────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Step 4: Translation Agent                                                │
│  Input:  {{response}}  (the ACTUAL generated response!)                   │
│  Output: {"translated": "Estimado cliente, le pido disculpas..."}        │
└──────────────────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
# 1. Start Ollama (requires ~4GB for llama3.2 model)
cd examples/a2a-real-world-integration
docker-compose up -d

# 2. Wait for model download (first time only)
docker-compose logs -f ollama-init
# Wait for "Model llama3.2 is ready!"

# 3. Run the demo
mvn clean compile exec:java

# 4. Open the Web UI
open http://localhost:9000
```

## Web UI

The demo includes a web interface at **http://localhost:9000** where you can:

1. Enter any customer complaint in the text area
2. Click "Process Through Pipeline"
3. Watch the complaint flow through all 4 agents with context chaining
4. See each step's output including sentiment, analysis, response, and translation

![Web UI Screenshot](docs/web-ui.png)

## What You'll See

The demo processes a customer complaint through 4 agents with context chaining:

```
================================================================================
  A2A Context Chaining Demo
  Multi-Agent Pipeline with Accumulated Context
================================================================================

Each agent receives outputs from ALL previous agents via {{variable}} templates:

  Step 1: Sentiment    -> Input: {{original}}
  Step 2: Analyzer     -> Input: {{original}} + {{sentiment}}
  Step 3: Response     -> Input: {{original}} + {{sentiment}} + {{analysis}}
  Step 4: Translation  -> Input: {{response}}
```

### Context Keys in Logs

Watch for the context accumulation in the logs:

```
[Step 1/4] Analyze customer sentiment and emotions
  Context keys available: []

[Step 2/4] Extract issues and entities with sentiment context
  Context keys available: [sentiment]

[Step 3/4] Generate response using sentiment and analysis
  Context keys available: [sentiment, analysis]

[Step 4/4] Translate response to Spanish
  Context keys available: [sentiment, analysis, response]
```

## How It Works

### 1. Define Steps with Templates

Each step uses `{{variable}}` placeholders that get substituted with previous outputs:

```java
List<ContextualStep> steps = List.of(
    // Step 1: Just the original message
    new ContextualStep(
        "Analyze sentiment",
        "http://localhost:9001",
        "sentiment",           // Output stored as "sentiment"
        "{{original}}"         // Template: just original message
    ),

    // Step 2: Original + sentiment from step 1
    new ContextualStep(
        "Analyze issues",
        "http://localhost:9002",
        "analysis",            // Output stored as "analysis"
        """
        CUSTOMER MESSAGE:
        {{original}}

        SENTIMENT ANALYSIS:
        {{sentiment}}

        Use the sentiment to prioritize issues.
        """
    ),

    // Step 3: All previous context
    new ContextualStep(
        "Generate response",
        "http://localhost:9003",
        "response",
        """
        {{original}}
        {{sentiment}}
        {{analysis}}

        Generate an appropriate response.
        """
    )
);
```

### 2. Execute with Context Chaining

```java
A2AOrchestrator orchestrator = new A2AOrchestrator(registryUrl);
List<WorkflowResult> results = orchestrator.executeContextualWorkflow(
    "My Pipeline",
    steps,
    customerMessage
);
```

### 3. Template Variables

| Variable | Description |
|----------|-------------|
| `{{original}}` | The original input message |
| `{{outputKey}}` | Output from a previous step (uses step's `outputKey`) |

## Key Classes

### ContextualStep

Defines a workflow step with template-based input:

```java
public class ContextualStep {
    public String description;    // Human-readable description
    public String agentUrl;       // Agent endpoint URL
    public String outputKey;      // Key to store result in context
    public String taskTemplate;   // Template with {{variable}} placeholders
}
```

### WorkflowContext

Accumulates outputs and performs template substitution:

```java
public class WorkflowContext {
    public String originalMessage;
    public Map<String, String> agentOutputs = new LinkedHashMap<>();

    public String buildPrompt(String template) {
        String result = template.replace("{{original}}", originalMessage);
        for (var entry : agentOutputs.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
```

## Benefits of Context Chaining

| Without Context Chaining | With Context Chaining |
|-------------------------|----------------------|
| Analyzer doesn't know customer is angry | Analyzer sees `very_negative` sentiment → sets P1 priority |
| Response is generic | Response addresses specific issues with appropriate empathy |
| Translator gets placeholder text | Translator translates the **actual** crafted response |

## File Structure

```
src/main/java/.../realworld/
├── RealA2ADemo.java              # Main demo application
├── llm/
│   ├── AgentPrompts.java         # Context-aware system prompts
│   ├── LLMAgentServer.java       # LLM-powered A2A agent
│   └── OllamaClient.java         # Ollama HTTP client
├── orchestrator/
│   └── A2AOrchestrator.java      # Context chaining orchestrator
└── web/
    └── WebUIServer.java          # Web UI for submitting complaints
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `llama3.2` | LLM model to use |

## Requirements

- Java 17+
- Maven 3.8+
- Docker and Docker Compose
- 8GB+ RAM (for Ollama)
- ~4GB disk space (for llama3.2 model)

## License

Apache License 2.0
