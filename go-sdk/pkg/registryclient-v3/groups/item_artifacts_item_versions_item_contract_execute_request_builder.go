package groups

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
)

// ItemArtifactsItemVersionsItemContractExecuteRequestBuilder execute contract rules against a data record.
type ItemArtifactsItemVersionsItemContractExecuteRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ItemArtifactsItemVersionsItemContractExecuteRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsItemContractExecuteRequestBuilderPostRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// NewItemArtifactsItemVersionsItemContractExecuteRequestBuilderInternal instantiates a new ItemArtifactsItemVersionsItemContractExecuteRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemContractExecuteRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemVersionsItemContractExecuteRequestBuilder) {
    m := &ItemArtifactsItemVersionsItemContractExecuteRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/contract/execute", pathParameters),
    }
    return m
}
// NewItemArtifactsItemVersionsItemContractExecuteRequestBuilder instantiates a new ItemArtifactsItemVersionsItemContractExecuteRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemContractExecuteRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemVersionsItemContractExecuteRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemArtifactsItemVersionsItemContractExecuteRequestBuilderInternal(urlParams, requestAdapter)
}
// Post executes contract rules against a data record and returns the result.
// Deprecated: This method is obsolete. Use PostAsExecutePostResponse instead.
// returns a ItemArtifactsItemVersionsItemContractExecuteResponseable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemVersionsItemContractExecuteRequestBuilder) Post(ctx context.Context, body ItemArtifactsItemVersionsItemContractExecutePostRequestBodyable, requestConfiguration *ItemArtifactsItemVersionsItemContractExecuteRequestBuilderPostRequestConfiguration)(ItemArtifactsItemVersionsItemContractExecuteResponseable, error) {
    requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemVersionsItemContractExecuteResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemVersionsItemContractExecuteResponseable), nil
}
// PostAsExecutePostResponse executes contract rules against a data record and returns the result.
// returns a ItemArtifactsItemVersionsItemContractExecutePostResponseable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemVersionsItemContractExecuteRequestBuilder) PostAsExecutePostResponse(ctx context.Context, body ItemArtifactsItemVersionsItemContractExecutePostRequestBodyable, requestConfiguration *ItemArtifactsItemVersionsItemContractExecuteRequestBuilderPostRequestConfiguration)(ItemArtifactsItemVersionsItemContractExecutePostResponseable, error) {
    requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemVersionsItemContractExecutePostResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemVersionsItemContractExecutePostResponseable), nil
}
// ToPostRequestInformation executes contract rules against a data record and returns the result.
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemVersionsItemContractExecuteRequestBuilder) ToPostRequestInformation(ctx context.Context, body ItemArtifactsItemVersionsItemContractExecutePostRequestBodyable, requestConfiguration *ItemArtifactsItemVersionsItemContractExecuteRequestBuilderPostRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
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
// returns a *ItemArtifactsItemVersionsItemContractExecuteRequestBuilder when successful
func (m *ItemArtifactsItemVersionsItemContractExecuteRequestBuilder) WithUrl(rawUrl string)(*ItemArtifactsItemVersionsItemContractExecuteRequestBuilder) {
    return NewItemArtifactsItemVersionsItemContractExecuteRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
