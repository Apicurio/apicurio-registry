# A2A Agent Card Example

This example demonstrates how to use Apicurio Registry to manage **A2A (Agent-to-Agent) Agent Cards** - JSON metadata documents that describe AI agents according to the [A2A Protocol](https://a2a-protocol.org/).

## What is A2A?

The A2A (Agent-to-Agent) protocol is an open standard that enables AI agents to discover and communicate with each other. An **Agent Card** is the fundamental metadata document that describes an agent's:

- **Identity**: Name, description, version, and URL
- **Capabilities**: Streaming support, push notifications
- **Skills**: What the agent can do (e.g., code generation, data analysis)
- **Authentication**: How to authenticate with the agent
- **Input/Output Modes**: Supported data formats (text, image, file, etc.)

## Why Use Apicurio Registry for Agent Cards?

Apicurio Registry provides enterprise-grade features for managing Agent Cards:

1. **Version Control**: Track changes to agent definitions over time
2. **Compatibility Rules**: Ensure backward-compatible evolution of agents
3. **Validation**: Validate Agent Cards against the A2A schema
4. **Discovery**: Search and discover registered agents
5. **Governance**: Apply rules and policies to agent definitions

## Prerequisites

- Java 17 or later
- Maven 3.8 or later
- Docker (for running Apicurio Registry)

## Quick Start

### 1. Start Apicurio Registry

```bash
# Using docker-compose (recommended)
docker-compose up -d

# Or run directly
docker run -it -p 8080:8080 apicurio/apicurio-registry:latest-snapshot
```

### 2. Build and Run the Example

```bash
# Build the example
mvn clean compile

# Run the example
mvn exec:java
```

## What the Example Demonstrates

### Demo 1: Agent Card CRUD Operations

Shows how to:
- Register an Agent Card in the registry
- Retrieve agent metadata
- Create new versions as capabilities evolve
- List all versions of an agent

### Demo 2: Compatibility Rules

Demonstrates safe evolution of Agent Cards:
- Adding skills is **backward compatible** (clients won't break)
- Removing skills would **break compatibility** (prevented by rules)
- Adding capabilities is safe, removing them is not

### Demo 3: Searching for Agents

Shows how to:
- Register multiple agents
- Search for agents by group
- Filter agents by type

### Demo 4: Minimal Agent Card

Demonstrates that only the `name` field is required:
```json
{
    "name": "Minimal Agent"
}
```

## Sample Agent Cards

The example includes realistic Agent Card samples:

### Code Assistant Agent (`code-assistant.json`)

A coding assistant with skills for:
- Code generation
- Code review
- Debugging assistance

### Data Analyst Agent (`data-analyst-v1.json`, `data-analyst-v2.json`)

A data analysis agent that evolves from v1 to v2:
- v1: Data analysis, visualization, report generation
- v2: Adds predictive analytics capability

## Agent Card Structure

```json
{
  "name": "My Agent",                    // Required
  "description": "What the agent does",  // Optional
  "version": "1.0.0",                    // Optional
  "url": "https://api.example.com",      // Optional - Agent endpoint
  "provider": {                          // Optional
    "organization": "Example Corp",
    "url": "https://example.com"
  },
  "capabilities": {                      // Optional
    "streaming": true,
    "pushNotifications": false
  },
  "skills": [                            // Optional
    {
      "id": "skill-id",                  // Required within skill
      "name": "Skill Name",              // Required within skill
      "description": "What the skill does",
      "tags": ["tag1", "tag2"],
      "examples": ["Example prompt 1"]
    }
  ],
  "defaultInputModes": ["text"],         // Optional
  "defaultOutputModes": ["text"],        // Optional
  "authentication": {                    // Optional
    "schemes": ["bearer", "api-key"]
  },
  "supportsExtendedAgentCard": false     // Optional
}
```

## Configuration

Set these environment variables to customize behavior:

| Variable | Description | Default |
|----------|-------------|---------|
| `REGISTRY_URL` | Registry API URL | `http://localhost:8080/apis/registry/v3` |
| `AUTH_TOKEN_ENDPOINT` | OAuth2 token endpoint | None (no auth) |
| `AUTH_CLIENT_ID` | OAuth2 client ID | - |
| `AUTH_CLIENT_SECRET` | OAuth2 client secret | - |

## Integration with A2A Discovery

Once registered in Apicurio Registry, Agent Cards can be:

1. **Discovered via `/.well-known/agent.json`**: The registry itself exposes its capabilities as an A2A agent
2. **Proxied via `/.well-known/agents/{groupId}/{artifactId}`**: Access registered Agent Cards through a standard discovery endpoint
3. **Searched and filtered**: Find agents by capabilities, skills, or other criteria

## Further Reading

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [Google A2A Announcement](https://developers.googleblog.com/en/a2a-a-new-era-of-agent-interoperability/)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [GitHub Issue #6996](https://github.com/Apicurio/apicurio-registry/issues/6996) - A2A Agent Card Implementation
