# Enterprise AI Agent Marketplace - Real-World A2A Example

This example demonstrates a **production-realistic enterprise scenario** where multiple AI agents from different vendors and domains are registered, discovered, and orchestrated using the [A2A (Agent-to-Agent) protocol](https://a2a-protocol.org/) with Apicurio Registry.

## Scenario Overview

An enterprise uses AI agents from multiple vendors organized by business domain:

| Domain | Agents | Purpose |
|--------|--------|---------|
| **Customer Support** | Zendesk AI, Intercom Conversation AI | Ticket management, live chat, sentiment analysis |
| **Finance** | Stripe Radar, Plaid Insights | Fraud detection, financial health, transaction analysis |
| **DevOps** | Datadog, PagerDuty, GitHub Security | Observability, incident management, security scanning |
| **Orchestration** | Enterprise Orchestrator | Discovers and coordinates other agents |

## Why This Matters

In real enterprise environments:

1. **Multi-Vendor Reality**: Organizations use best-of-breed AI tools from different vendors
2. **Cross-Domain Workflows**: Business processes span multiple domains (e.g., fraud investigation touches finance, support, and security)
3. **Governance Requirements**: AI agents need version control, compatibility rules, and audit trails
4. **Discovery Needs**: Orchestrators must find agents dynamically based on capabilities

## What This Example Demonstrates

### Phase 1: Domain-Organized Agent Registration
- Agents registered in domain-specific groups (`enterprise.agents.finance`, `enterprise.agents.devops`, etc.)
- Each agent has realistic skills, capabilities, and authentication requirements
- Mirrors how enterprises would organize their AI agent portfolio

### Phase 2: A2A Protocol Discovery
- `/.well-known/agent.json` - Registry exposes itself as an A2A agent
- `/.well-known/agents` - Search and filter registered agents
- `/.well-known/agents/{groupId}/{artifactId}` - Retrieve specific agent cards
- Standard endpoints enable interoperability across platforms

### Phase 3: Skill-Based Agent Search
- Find agents by capability for workflow composition
- Search examples:
  - "Find agents with fraud detection skills"
  - "Which agents support streaming?"
  - "List all DevOps agents for incident handling"

### Phase 4: Agent Evolution with Governance
- Compatibility rules prevent breaking changes
- Safe evolution: Adding new skills is backward compatible
- Breaking change prevention: Can't remove skills that orchestrations depend on
- Version history tracking for audit

### Phase 5: Multi-Agent Orchestration
- Realistic scenario: Suspicious transaction investigation
- Spans Finance, Support, and DevOps domains
- Demonstrates how orchestrators compose multi-agent workflows

## Agent Cards Included

### Customer Support Agents

**Zendesk AI Support Agent** (`zendesk-ai-agent.json`)
- Ticket triage and classification
- Sentiment analysis
- Automated response generation
- Escalation prediction
- Knowledge base search

**Intercom Conversation AI** (`intercom-conversation-agent.json`)
- Live chat response
- Intent recognition
- Proactive outreach
- Human handoff orchestration
- Multilingual support

### Finance Agents

**Stripe Radar AI Agent** (`stripe-fraud-detection-agent.json`)
- Transaction risk scoring
- Dispute prediction
- Custom rule recommendations
- Behavioral anomaly detection
- Fraud investigation assistance

**Plaid Financial Insights Agent** (`plaid-financial-insights-agent.json`)
- Transaction categorization
- Income verification
- Spending pattern analysis
- Cash flow prediction
- Financial health scoring
- Subscription detection

### DevOps Agents

**Datadog AI Observability Agent** (`datadog-observability-agent.json`)
- Metric anomaly detection
- Root cause analysis
- Intelligent log analysis
- Incident triage
- Remediation recommendations
- Capacity forecasting
- *v2 adds*: Predictive alerting, change correlation

**PagerDuty AI Incident Commander** (`pagerduty-incident-agent.json`)
- Automated incident declaration
- Responder mobilization
- Stakeholder communication
- Timeline reconstruction
- Post-incident report generation
- Similar incident matching

**GitHub Advanced Security Agent** (`github-copilot-security-agent.json`)
- Code security scanning
- Secret detection
- Dependency vulnerability analysis
- Automated security review
- Security fix suggestions
- Compliance checking

### Orchestration Agent

**Enterprise Agent Orchestrator** (`orchestrator-agent.json`)
- Agent discovery via A2A registry
- Workflow composition
- Task delegation
- Result aggregation
- Conversation routing
- Agent health monitoring

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker

### 1. Start Apicurio Registry

```bash
docker-compose up -d
```

Wait for services to be healthy:
```bash
docker-compose ps
```

### 2. Build and Run the Example

```bash
# Build
mvn clean compile

# Run
mvn exec:java
```

### 3. Explore the Results

- **UI**: http://localhost:8888 - Browse registered agents
- **A2A Discovery**: http://localhost:8080/.well-known/agents - Search agents
- **Registry Agent**: http://localhost:8080/.well-known/agent.json - Registry's own capabilities

## Expected Output

```
================================================================================
Enterprise AI Agent Marketplace - Real-World A2A Example
================================================================================

This example demonstrates a production-like enterprise scenario with
AI agents from Zendesk, Intercom, Stripe, Plaid, Datadog, PagerDuty, and GitHub.

--------------------------------------------------------------------------------
Phase 1: Registering Enterprise AI Agents by Domain
--------------------------------------------------------------------------------

[Customer Support Domain]
  Registered: Zendesk AI Support Agent v2.4.0 (Zendesk Inc.)
  Registered: Intercom Conversation AI v3.1.0 (Intercom Inc.)

[Finance Domain]
  Registered: Stripe Radar AI Agent v4.2.1 (Stripe Inc.)
  Registered: Plaid Financial Insights Agent v2.8.0 (Plaid Inc.)

[DevOps Domain]
  Registered: Datadog AI Observability Agent v3.5.0 (Datadog Inc.)
  Registered: PagerDuty AI Incident Commander v2.3.0 (PagerDuty Inc.)
  Registered: GitHub Advanced Security Agent v2.1.0 (GitHub Inc.)

[Orchestration Domain]
  Registered: Enterprise Agent Orchestrator v1.0.0 (Enterprise AI Platform)

Successfully registered 8 enterprise agents across 4 domains

... (discovery, search, evolution, and orchestration phases follow)
```

## Key Concepts

### Group-Based Organization
```
enterprise.agents.customer-support/
  ├── zendesk-support-agent
  └── intercom-conversation-agent

enterprise.agents.finance/
  ├── stripe-radar-agent
  └── plaid-insights-agent

enterprise.agents.devops/
  ├── datadog-observability-agent
  ├── pagerduty-incident-agent
  └── github-security-agent

enterprise.agents.orchestration/
  └── enterprise-orchestrator
```

### A2A Well-Known Endpoints
```
GET /.well-known/agent.json           # Registry's own agent card
GET /.well-known/agents               # Search all agents
GET /.well-known/agents?skill=fraud   # Filter by skill
GET /.well-known/agents/{group}/{id}  # Get specific agent
```

### Compatibility Rules
```java
// Enable backward compatibility for safe evolution
CreateRule rule = new CreateRule();
rule.setRuleType(RuleType.COMPATIBILITY);
rule.setConfig("BACKWARD");
client.groups().byGroupId(GROUP_DEVOPS)
      .artifacts().byArtifactId("datadog-observability-agent")
      .rules().post(rule);
```

## Integration Patterns

### Pattern 1: Skill-Based Agent Discovery
```java
// Find agents capable of fraud detection
ArtifactSearchResults agents = client.search().artifacts().get(config -> {
    config.queryParameters.labels = List.of("a2a.skill.fraud-detection");
});
```

### Pattern 2: Domain-Scoped Search
```java
// Get all DevOps agents
ArtifactSearchResults devops = client.groups()
    .byGroupId("enterprise.agents.devops")
    .artifacts().get();
```

### Pattern 3: Version Evolution
```java
// Add new version with enhanced capabilities
CreateVersion version = new CreateVersion();
version.setVersion("4.0.0");
version.setContent(new VersionContent());
version.getContent().setContent(enhancedAgentCard);
client.groups().byGroupId(GROUP)
      .artifacts().byArtifactId(ARTIFACT_ID)
      .versions().post(version);
```

## Production Considerations

When adapting this example for production:

1. **Authentication**: Configure OAuth2/OIDC for agent registration and discovery
2. **RBAC**: Set up role-based access for different teams (DevOps team manages DevOps agents)
3. **Persistence**: Use PostgreSQL or Kafka-SQL storage instead of in-memory H2
4. **Monitoring**: Enable metrics export for registry health monitoring
5. **High Availability**: Deploy multiple registry instances behind a load balancer
6. **Backup**: Implement regular backup of agent card registry

## Further Reading

- [A2A Protocol Specification](https://a2a-protocol.org/latest/specification/)
- [Google A2A Announcement](https://developers.googleblog.com/en/a2a-a-new-era-of-agent-interoperability/)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Basic A2A Agent Card Example](../a2a-agent-card/README.md)
