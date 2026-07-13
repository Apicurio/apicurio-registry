package groups

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
)

// ItemArtifactsItemContractCompatibilityGroupRequestBuilder manage the compatibility group for an artifact's contract.
type ItemArtifactsItemContractCompatibilityGroupRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ItemArtifactsItemContractCompatibilityGroupRequestBuilderGetQueryParameters returns the compatibility group for the artifact's contract.
type ItemArtifactsItemContractCompatibilityGroupRequestBuilderGetQueryParameters struct {
    ContractId *string `uriparametername:"contractId"`
}
// ItemArtifactsItemContractCompatibilityGroupRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemContractCompatibilityGroupRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
    // Request query parameters
    QueryParameters *ItemArtifactsItemContractCompatibilityGroupRequestBuilderGetQueryParameters
}
// ItemArtifactsItemContractCompatibilityGroupRequestBuilderPutRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemContractCompatibilityGroupRequestBuilderPutRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// NewItemArtifactsItemContractCompatibilityGroupRequestBuilderInternal instantiates a new ItemArtifactsItemContractCompatibilityGroupRequestBuilder and sets the default values.
func NewItemArtifactsItemContractCompatibilityGroupRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractCompatibilityGroupRequestBuilder) {
    m := &ItemArtifactsItemContractCompatibilityGroupRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group?contractId={contractId}", pathParameters),
    }
    return m
}
// NewItemArtifactsItemContractCompatibilityGroupRequestBuilder instantiates a new ItemArtifactsItemContractCompatibilityGroupRequestBuilder and sets the default values.
func NewItemArtifactsItemContractCompatibilityGroupRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractCompatibilityGroupRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemArtifactsItemContractCompatibilityGroupRequestBuilderInternal(urlParams, requestAdapter)
}
// Get returns the compatibility group for the artifact's contract.
// Deprecated: This method is obsolete. Use GetAsCompatibilityGroupGetResponse instead.
// returns a ItemArtifactsItemContractCompatibilityGroupResponseable when successful
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractCompatibilityGroupRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemContractCompatibilityGroupRequestBuilderGetRequestConfiguration)(ItemArtifactsItemContractCompatibilityGroupResponseable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemContractCompatibilityGroupResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemContractCompatibilityGroupResponseable), nil
}
// GetAsCompatibilityGroupGetResponse returns the compatibility group for the artifact's contract.
// returns a ItemArtifactsItemContractCompatibilityGroupGetResponseable when successful
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractCompatibilityGroupRequestBuilder) GetAsCompatibilityGroupGetResponse(ctx context.Context, requestConfiguration *ItemArtifactsItemContractCompatibilityGroupRequestBuilderGetRequestConfiguration)(ItemArtifactsItemContractCompatibilityGroupGetResponseable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemContractCompatibilityGroupGetResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemContractCompatibilityGroupGetResponseable), nil
}
// Put sets the compatibility group for the artifact's contract.
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractCompatibilityGroupRequestBuilder) Put(ctx context.Context, body ItemArtifactsItemContractCompatibilityGroupPutRequestBodyable, requestConfiguration *ItemArtifactsItemContractCompatibilityGroupRequestBuilderPutRequestConfiguration)(error) {
    requestInfo, err := m.ToPutRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
    if err != nil {
        return err
    }
    return nil
}
// ToGetRequestInformation returns the compatibility group for the artifact's contract.
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemContractCompatibilityGroupRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemContractCompatibilityGroupRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// ToPutRequestInformation sets the compatibility group for the artifact's contract.
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemContractCompatibilityGroupRequestBuilder) ToPutRequestInformation(ctx context.Context, body ItemArtifactsItemContractCompatibilityGroupPutRequestBodyable, requestConfiguration *ItemArtifactsItemContractCompatibilityGroupRequestBuilderPutRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.PUT, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group", m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    err := requestInfo.SetContentFromParsable(ctx, m.BaseRequestBuilder.RequestAdapter, "application/json", body)
    if err != nil {
        return nil, err
    }
    return requestInfo, nil
}
// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ItemArtifactsItemContractCompatibilityGroupRequestBuilder when successful
func (m *ItemArtifactsItemContractCompatibilityGroupRequestBuilder) WithUrl(rawUrl string)(*ItemArtifactsItemContractCompatibilityGroupRequestBuilder) {
    return NewItemArtifactsItemContractCompatibilityGroupRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
