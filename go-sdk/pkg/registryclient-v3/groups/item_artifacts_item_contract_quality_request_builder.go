package groups

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
)

// ItemArtifactsItemContractQualityRequestBuilder get quality score for a contract.
type ItemArtifactsItemContractQualityRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ItemArtifactsItemContractQualityRequestBuilderGetQueryParameters returns the quality score for a contract, with breakdown by completeness, compliance, and stability.
type ItemArtifactsItemContractQualityRequestBuilderGetQueryParameters struct {
    ContractId *string `uriparametername:"contractId"`
}
// ItemArtifactsItemContractQualityRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemContractQualityRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
    // Request query parameters
    QueryParameters *ItemArtifactsItemContractQualityRequestBuilderGetQueryParameters
}
// NewItemArtifactsItemContractQualityRequestBuilderInternal instantiates a new ItemArtifactsItemContractQualityRequestBuilder and sets the default values.
func NewItemArtifactsItemContractQualityRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractQualityRequestBuilder) {
    m := &ItemArtifactsItemContractQualityRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/contract/quality?contractId={contractId}", pathParameters),
    }
    return m
}
// NewItemArtifactsItemContractQualityRequestBuilder instantiates a new ItemArtifactsItemContractQualityRequestBuilder and sets the default values.
func NewItemArtifactsItemContractQualityRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractQualityRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemArtifactsItemContractQualityRequestBuilderInternal(urlParams, requestAdapter)
}
// Get returns the quality score for a contract, with breakdown by completeness, compliance, and stability.
// Deprecated: This method is obsolete. Use GetAsQualityGetResponse instead.
// returns a ItemArtifactsItemContractQualityResponseable when successful
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractQualityRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemContractQualityRequestBuilderGetRequestConfiguration)(ItemArtifactsItemContractQualityResponseable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemContractQualityResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemContractQualityResponseable), nil
}
// GetAsQualityGetResponse returns the quality score for a contract, with breakdown by completeness, compliance, and stability.
// returns a ItemArtifactsItemContractQualityGetResponseable when successful
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractQualityRequestBuilder) GetAsQualityGetResponse(ctx context.Context, requestConfiguration *ItemArtifactsItemContractQualityRequestBuilderGetRequestConfiguration)(ItemArtifactsItemContractQualityGetResponseable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemContractQualityGetResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemContractQualityGetResponseable), nil
}
// ToGetRequestInformation returns the quality score for a contract, with breakdown by completeness, compliance, and stability.
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemContractQualityRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemContractQualityRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *ItemArtifactsItemContractQualityRequestBuilder when successful
func (m *ItemArtifactsItemContractQualityRequestBuilder) WithUrl(rawUrl string)(*ItemArtifactsItemContractQualityRequestBuilder) {
    return NewItemArtifactsItemContractQualityRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
