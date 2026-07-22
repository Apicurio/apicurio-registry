package wellknown

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// AgentsPublicRequestBuilder search for publicly visible agent cards.
type AgentsPublicRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// AgentsPublicRequestBuilderGetQueryParameters returns a paginated list of agent cards that are publicly visible without authentication.
type AgentsPublicRequestBuilderGetQueryParameters struct {
	// The number of agents to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of agents to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
}

// AgentsPublicRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type AgentsPublicRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *AgentsPublicRequestBuilderGetQueryParameters
}

// NewAgentsPublicRequestBuilderInternal instantiates a new AgentsPublicRequestBuilder and sets the default values.
func NewAgentsPublicRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentsPublicRequestBuilder {
	m := &AgentsPublicRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/agents/public{?limit*,offset*}", pathParameters),
	}
	return m
}

// NewAgentsPublicRequestBuilder instantiates a new AgentsPublicRequestBuilder and sets the default values.
func NewAgentsPublicRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentsPublicRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewAgentsPublicRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a paginated list of agent cards that are publicly visible without authentication.
// returns a AgentSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 500 status code
func (m *AgentsPublicRequestBuilder) Get(ctx context.Context, requestConfiguration *AgentsPublicRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.AgentSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
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

// ToGetRequestInformation returns a paginated list of agent cards that are publicly visible without authentication.
// returns a *RequestInformation when successful
func (m *AgentsPublicRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *AgentsPublicRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *AgentsPublicRequestBuilder when successful
func (m *AgentsPublicRequestBuilder) WithUrl(rawUrl string) *AgentsPublicRequestBuilder {
	return NewAgentsPublicRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
