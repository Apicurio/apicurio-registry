package search

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
    i79b48f6ce5094b74d8b1bf3b99114d817cca708e6d04a95bbbcbde8b82b78374 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/search/contracts"
)

// ContractsRequestBuilder search for contracts.
type ContractsRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ContractsRequestBuilderGetQueryParameters searches for artifacts that have contract metadata labels. Filters by status, owner team, and compatibility group.
type ContractsRequestBuilderGetQueryParameters struct {
    // Filter by compatibility group.
    CompatibilityGroup *string `uriparametername:"compatibilityGroup"`
    // Maximum number of results to return.
    Limit *int32 `uriparametername:"limit"`
    // Number of results to skip.
    Offset *int32 `uriparametername:"offset"`
    // Sort order (asc or desc).
    // Deprecated: This property is deprecated, use OrderAsGetOrderQueryParameterType instead
    Order *string `uriparametername:"order"`
    // Sort order (asc or desc).
    OrderAsGetOrderQueryParameterType *i79b48f6ce5094b74d8b1bf3b99114d817cca708e6d04a95bbbcbde8b82b78374.GetOrderQueryParameterType `uriparametername:"order"`
    // Field to sort by.
    // Deprecated: This property is deprecated, use OrderbyAsGetOrderbyQueryParameterType instead
    Orderby *string `uriparametername:"orderby"`
    // Field to sort by.
    OrderbyAsGetOrderbyQueryParameterType *i79b48f6ce5094b74d8b1bf3b99114d817cca708e6d04a95bbbcbde8b82b78374.GetOrderbyQueryParameterType `uriparametername:"orderby"`
    // Filter by owner team.
    OwnerTeam *string `uriparametername:"ownerTeam"`
    // Filter by contract status (DRAFT, STABLE, DEPRECATED).
    Status *string `uriparametername:"status"`
}
// ContractsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ContractsRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
    // Request query parameters
    QueryParameters *ContractsRequestBuilderGetQueryParameters
}
// NewContractsRequestBuilderInternal instantiates a new ContractsRequestBuilder and sets the default values.
func NewContractsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ContractsRequestBuilder) {
    m := &ContractsRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/search/contracts{?compatibilityGroup*,limit*,offset*,order*,orderby*,ownerTeam*,status*}", pathParameters),
    }
    return m
}
// NewContractsRequestBuilder instantiates a new ContractsRequestBuilder and sets the default values.
func NewContractsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ContractsRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewContractsRequestBuilderInternal(urlParams, requestAdapter)
}
// Get searches for artifacts that have contract metadata labels. Filters by status, owner team, and compatibility group.
// returns a ArtifactSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ContractsRequestBuilder) Get(ctx context.Context, requestConfiguration *ContractsRequestBuilderGetRequestConfiguration)(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSearchResultsable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateArtifactSearchResultsFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSearchResultsable), nil
}
// ToGetRequestInformation searches for artifacts that have contract metadata labels. Filters by status, owner team, and compatibility group.
// returns a *RequestInformation when successful
func (m *ContractsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ContractsRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *ContractsRequestBuilder when successful
func (m *ContractsRequestBuilder) WithUrl(rawUrl string)(*ContractsRequestBuilder) {
    return NewContractsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
