package wellknown

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// AgentsItemWithArtifactItemRequestBuilder get a specific registered agent card.
type AgentsItemWithArtifactItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// AgentsItemWithArtifactItemRequestBuilderGetQueryParameters returns a specific registered Agent Card by group and artifact ID. Optionally specify a version.
type AgentsItemWithArtifactItemRequestBuilderGetQueryParameters struct {
	// The version of the agent card to retrieve. Defaults to the latest version.
	Version *string `uriparametername:"version"`
}

// AgentsItemWithArtifactItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type AgentsItemWithArtifactItemRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *AgentsItemWithArtifactItemRequestBuilderGetQueryParameters
}

// NewAgentsItemWithArtifactItemRequestBuilderInternal instantiates a new AgentsItemWithArtifactItemRequestBuilder and sets the default values.
func NewAgentsItemWithArtifactItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentsItemWithArtifactItemRequestBuilder {
	m := &AgentsItemWithArtifactItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/agents/{groupId}/{artifactId}{?version*}", pathParameters),
	}
	return m
}

// NewAgentsItemWithArtifactItemRequestBuilder instantiates a new AgentsItemWithArtifactItemRequestBuilder and sets the default values.
func NewAgentsItemWithArtifactItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *AgentsItemWithArtifactItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewAgentsItemWithArtifactItemRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a specific registered Agent Card by group and artifact ID. Optionally specify a version.
// returns a []byte when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *AgentsItemWithArtifactItemRequestBuilder) Get(ctx context.Context, requestConfiguration *AgentsItemWithArtifactItemRequestBuilderGetRequestConfiguration) ([]byte, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.SendPrimitive(ctx, requestInfo, "[]byte", errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.([]byte), nil
}

// ToGetRequestInformation returns a specific registered Agent Card by group and artifact ID. Optionally specify a version.
// returns a *RequestInformation when successful
func (m *AgentsItemWithArtifactItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *AgentsItemWithArtifactItemRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *AgentsItemWithArtifactItemRequestBuilder when successful
func (m *AgentsItemWithArtifactItemRequestBuilder) WithUrl(rawUrl string) *AgentsItemWithArtifactItemRequestBuilder {
	return NewAgentsItemWithArtifactItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
