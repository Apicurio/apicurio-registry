package wellknown

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// WellKnownRequestBuilder builds and executes requests for operations under \well-known
type WellKnownRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// A2a get the Agent Card via the canonical A2A v1.0 discovery path.
// returns a *A2aRequestBuilder when successful
func (m *WellKnownRequestBuilder) A2a() *A2aRequestBuilder {
	return NewA2aRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// AgentCardJson get the Agent Card (watsonx Orchestrate compatible path).
// returns a *AgentCardJsonRequestBuilder when successful
func (m *WellKnownRequestBuilder) AgentCardJson() *AgentCardJsonRequestBuilder {
	return NewAgentCardJsonRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// AgentJson get the Agent Card for this registry instance.
// returns a *AgentJsonRequestBuilder when successful
func (m *WellKnownRequestBuilder) AgentJson() *AgentJsonRequestBuilder {
	return NewAgentJsonRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Agents search for registered agent cards.
// returns a *AgentsRequestBuilder when successful
func (m *WellKnownRequestBuilder) Agents() *AgentsRequestBuilder {
	return NewAgentsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// NewWellKnownRequestBuilderInternal instantiates a new WellKnownRequestBuilder and sets the default values.
func NewWellKnownRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *WellKnownRequestBuilder {
	m := &WellKnownRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known", pathParameters),
	}
	return m
}

// NewWellKnownRequestBuilder instantiates a new WellKnownRequestBuilder and sets the default values.
func NewWellKnownRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *WellKnownRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewWellKnownRequestBuilderInternal(urlParams, requestAdapter)
}

// McpTools search for registered MCP tool definitions.
// returns a *McpToolsRequestBuilder when successful
func (m *WellKnownRequestBuilder) McpTools() *McpToolsRequestBuilder {
	return NewMcpToolsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Schemas the schemas property
// returns a *SchemasRequestBuilder when successful
func (m *WellKnownRequestBuilder) Schemas() *SchemasRequestBuilder {
	return NewSchemasRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
