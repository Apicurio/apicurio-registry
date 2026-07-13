package groups

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
)

// ItemArtifactsItemContractAuditRequestBuilder get the contract audit log for an artifact.
type ItemArtifactsItemContractAuditRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ItemArtifactsItemContractAuditRequestBuilderGetQueryParameters returns a paginated audit log of contract operations for the specified artifact.
type ItemArtifactsItemContractAuditRequestBuilderGetQueryParameters struct {
    Limit *int32 `uriparametername:"limit"`
    Offset *int32 `uriparametername:"offset"`
}
// ItemArtifactsItemContractAuditRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemContractAuditRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
    // Request query parameters
    QueryParameters *ItemArtifactsItemContractAuditRequestBuilderGetQueryParameters
}
// NewItemArtifactsItemContractAuditRequestBuilderInternal instantiates a new ItemArtifactsItemContractAuditRequestBuilder and sets the default values.
func NewItemArtifactsItemContractAuditRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractAuditRequestBuilder) {
    m := &ItemArtifactsItemContractAuditRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/contract/audit{?limit*,offset*}", pathParameters),
    }
    return m
}
// NewItemArtifactsItemContractAuditRequestBuilder instantiates a new ItemArtifactsItemContractAuditRequestBuilder and sets the default values.
func NewItemArtifactsItemContractAuditRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractAuditRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemArtifactsItemContractAuditRequestBuilderInternal(urlParams, requestAdapter)
}
// Get returns a paginated audit log of contract operations for the specified artifact.
// returns a []ItemArtifactsItemContractAuditable when successful
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractAuditRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemContractAuditRequestBuilderGetRequestConfiguration)([]ItemArtifactsItemContractAuditable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, CreateItemArtifactsItemContractAuditFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    val := make([]ItemArtifactsItemContractAuditable, len(res))
    for i, v := range res {
        if v != nil {
            val[i] = v.(ItemArtifactsItemContractAuditable)
        }
    }
    return val, nil
}
// ToGetRequestInformation returns a paginated audit log of contract operations for the specified artifact.
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemContractAuditRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemContractAuditRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *ItemArtifactsItemContractAuditRequestBuilder when successful
func (m *ItemArtifactsItemContractAuditRequestBuilder) WithUrl(rawUrl string)(*ItemArtifactsItemContractAuditRequestBuilder) {
    return NewItemArtifactsItemContractAuditRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
