package wellknown

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// McpToolsRequestBuilder search for registered MCP tool definitions.
type McpToolsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// McpToolsRequestBuilderGetQueryParameters returns a paginated list of registered MCP tool definitions, optionally filtered by name or parameter.
type McpToolsRequestBuilderGetQueryParameters struct {
	// The number of tools to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// Filter by tool name (partial match).
	Name *string `uriparametername:"name"`
	// The number of tools to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
	// Filter by input parameter name. Can be specified multiple times.
	Parameter []string `uriparametername:"parameter"`
}

// McpToolsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type McpToolsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *McpToolsRequestBuilderGetQueryParameters
}

// ByGroupId gets an item from the github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3.wellKnown.mcpTools.item collection
// returns a *McpToolsWithGroupItemRequestBuilder when successful
func (m *McpToolsRequestBuilder) ByGroupId(groupId string) *McpToolsWithGroupItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if groupId != "" {
		urlTplParams["groupId"] = groupId
	}
	return NewMcpToolsWithGroupItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewMcpToolsRequestBuilderInternal instantiates a new McpToolsRequestBuilder and sets the default values.
func NewMcpToolsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *McpToolsRequestBuilder {
	m := &McpToolsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/mcp-tools{?limit*,name*,offset*,parameter*}", pathParameters),
	}
	return m
}

// NewMcpToolsRequestBuilder instantiates a new McpToolsRequestBuilder and sets the default values.
func NewMcpToolsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *McpToolsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewMcpToolsRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a paginated list of registered MCP tool definitions, optionally filtered by name or parameter.
// returns a McpToolSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *McpToolsRequestBuilder) Get(ctx context.Context, requestConfiguration *McpToolsRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.McpToolSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateMcpToolSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.McpToolSearchResultsable), nil
}

// ToGetRequestInformation returns a paginated list of registered MCP tool definitions, optionally filtered by name or parameter.
// returns a *RequestInformation when successful
func (m *McpToolsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *McpToolsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *McpToolsRequestBuilder when successful
func (m *McpToolsRequestBuilder) WithUrl(rawUrl string) *McpToolsRequestBuilder {
	return NewMcpToolsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
