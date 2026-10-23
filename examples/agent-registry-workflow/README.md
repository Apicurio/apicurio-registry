# Agent Registry Workflow with Quarkus LangChain4j

This sample combines Apicurio Registry with the Quarkus LangChain4j A2A and MCP
discovery extensions to demonstrate:

- Agent Card publication and discovery through Registry.
- A real summarize → translate workflow using two remote A2A agents.
- Registry-backed MCP server discovery, connection and Weather tool invocation.
- Compatibility rules rejecting a removed skill while preserving accepted content,
  and accepting an additive change.

## Sample source and instructions

**Preview:** the sample is published on a contributor branch and has not yet merged
into Quarkus LangChain4j:

- [Sample README and run instructions](https://github.com/carlesarnal/quarkus-langchain4j/tree/sample/agent-registry-workflow/samples/agent-registry-workflow)
- [Verification record](https://github.com/carlesarnal/quarkus-langchain4j/blob/sample/agent-registry-workflow/samples/agent-registry-workflow/VERIFICATION.md)

The implementation and detailed setup instructions live in the Quarkus LangChain4j
repository. This directory is a reference only. Once the sample merges upstream,
replace the preview links with its canonical upstream location.

The sample uses these existing upstream extensions:

- [A2A Apicurio Registry](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/a2a-apicurio-registry)
- [MCP Apicurio Registry](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/mcp-apicurio-registry)

It runs locally without authentication and uses fictional weather data. MCP
discovery currently uses `MCP_TOOL` artifacts with connection labels. Compatibility
checks govern stored definitions; they do not block traffic to a running server.
See the sample README for prerequisites, supported versions and remaining limitations.
