package wellknown

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// AgentJsonRequestBuilder get the Agent Card for this registry instance.
type AgentJsonRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// AgentJsonRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type AgentJsonRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewAgentJsonRequestBuilderInternal instantiates a new AgentJsonRequestBuilder and sets the default values.
func NewAgentJsonRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentJsonRequestBuilder {
	m := &AgentJsonRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/agent.json", pathParameters),
	}
	return m
}

// NewAgentJsonRequestBuilder instantiates a new AgentJsonRequestBuilder and sets the default values.
func NewAgentJsonRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentJsonRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewAgentJsonRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns the Agent Card for this Apicurio Registry instance, enabling A2A protocol discovery of the registry as an agent.
// returns a AgentCardable when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *AgentJsonRequestBuilder) Get(ctx context.Context, requestConfiguration *AgentJsonRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.AgentCardable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateAgentCardFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.AgentCardable), nil
}

// ToGetRequestInformation returns the Agent Card for this Apicurio Registry instance, enabling A2A protocol discovery of the registry as an agent.
// returns a *RequestInformation when successful
func (m *AgentJsonRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *AgentJsonRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *AgentJsonRequestBuilder when successful
func (m *AgentJsonRequestBuilder) WithUrl(rawUrl string) *AgentJsonRequestBuilder {
	return NewAgentJsonRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
