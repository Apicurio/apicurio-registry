package wellknown

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// AgentsEntitledRequestBuilder search for agent cards the caller is entitled to.
type AgentsEntitledRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// AgentsEntitledRequestBuilderGetQueryParameters returns a paginated list of agent cards that the authenticated caller is entitled to access.
type AgentsEntitledRequestBuilderGetQueryParameters struct {
	// The number of agents to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of agents to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
}

// AgentsEntitledRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type AgentsEntitledRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *AgentsEntitledRequestBuilderGetQueryParameters
}

// NewAgentsEntitledRequestBuilderInternal instantiates a new AgentsEntitledRequestBuilder and sets the default values.
func NewAgentsEntitledRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentsEntitledRequestBuilder {
	m := &AgentsEntitledRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/agents/entitled{?limit*,offset*}", pathParameters),
	}
	return m
}

// NewAgentsEntitledRequestBuilder instantiates a new AgentsEntitledRequestBuilder and sets the default values.
func NewAgentsEntitledRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentsEntitledRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewAgentsEntitledRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a paginated list of agent cards that the authenticated caller is entitled to access.
// returns a AgentSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *AgentsEntitledRequestBuilder) Get(ctx context.Context, requestConfiguration *AgentsEntitledRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.AgentSearchResultsable, error) {
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

// ToGetRequestInformation returns a paginated list of agent cards that the authenticated caller is entitled to access.
// returns a *RequestInformation when successful
func (m *AgentsEntitledRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *AgentsEntitledRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *AgentsEntitledRequestBuilder when successful
func (m *AgentsEntitledRequestBuilder) WithUrl(rawUrl string) *AgentsEntitledRequestBuilder {
	return NewAgentsEntitledRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
