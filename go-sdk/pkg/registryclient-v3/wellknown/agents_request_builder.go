package wellknown

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// AgentsRequestBuilder search for registered agent cards.
type AgentsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// AgentsRequestBuilderGetQueryParameters returns a paginated list of registered agent cards, optionally filtered by name, skills, capabilities, or I/O modes.
type AgentsRequestBuilderGetQueryParameters struct {
	// Filter by capability (e.g., streaming:true).
	Capability []string `uriparametername:"capability"`
	// Filter by input mode (e.g., text, image).
	InputMode []string `uriparametername:"inputMode"`
	// The number of agents to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// Filter by agent name (partial match).
	Name *string `uriparametername:"name"`
	// The number of agents to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
	// Filter by output mode.
	OutputMode []string `uriparametername:"outputMode"`
	// Filter by skill ID. Can be specified multiple times.
	Skill []string `uriparametername:"skill"`
}

// AgentsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type AgentsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *AgentsRequestBuilderGetQueryParameters
}

// ByGroupId gets an item from the github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3.wellKnown.agents.item collection
// returns a *AgentsWithGroupItemRequestBuilder when successful
func (m *AgentsRequestBuilder) ByGroupId(groupId string) *AgentsWithGroupItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if groupId != "" {
		urlTplParams["groupId"] = groupId
	}
	return NewAgentsWithGroupItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewAgentsRequestBuilderInternal instantiates a new AgentsRequestBuilder and sets the default values.
func NewAgentsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentsRequestBuilder {
	m := &AgentsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/agents{?capability*,inputMode*,limit*,name*,offset*,outputMode*,skill*}", pathParameters),
	}
	return m
}

// NewAgentsRequestBuilder instantiates a new AgentsRequestBuilder and sets the default values.
func NewAgentsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewAgentsRequestBuilderInternal(urlParams, requestAdapter)
}

// Entitled search for agent cards the caller is entitled to.
// returns a *AgentsEntitledRequestBuilder when successful
func (m *AgentsRequestBuilder) Entitled() *AgentsEntitledRequestBuilder {
	return NewAgentsEntitledRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Get returns a paginated list of registered agent cards, optionally filtered by name, skills, capabilities, or I/O modes.
// returns a AgentSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *AgentsRequestBuilder) Get(ctx context.Context, requestConfiguration *AgentsRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.AgentSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateAgentSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.AgentSearchResultsable), nil
}

// Public search for publicly visible agent cards.
// returns a *AgentsPublicRequestBuilder when successful
func (m *AgentsRequestBuilder) Public() *AgentsPublicRequestBuilder {
	return NewAgentsPublicRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Search advanced search for agent cards with structured filters.
// returns a *AgentsSearchRequestBuilder when successful
func (m *AgentsRequestBuilder) Search() *AgentsSearchRequestBuilder {
	return NewAgentsSearchRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// ToGetRequestInformation returns a paginated list of registered agent cards, optionally filtered by name, skills, capabilities, or I/O modes.
// returns a *RequestInformation when successful
func (m *AgentsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *AgentsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		if requestConfiguration.QueryParameters != nil {
			requestInfo.AddQueryParameters(*(requestConfiguration.QueryParameters))
		}
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *AgentsRequestBuilder when successful
func (m *AgentsRequestBuilder) WithUrl(rawUrl string) *AgentsRequestBuilder {
	return NewAgentsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
