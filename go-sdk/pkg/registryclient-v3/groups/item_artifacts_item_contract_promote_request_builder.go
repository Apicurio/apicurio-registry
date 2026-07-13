package groups

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
)

// ItemArtifactsItemContractPromoteRequestBuilder promote a contract to the next deployment stage.
type ItemArtifactsItemContractPromoteRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ItemArtifactsItemContractPromoteRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemContractPromoteRequestBuilderPostRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// NewItemArtifactsItemContractPromoteRequestBuilderInternal instantiates a new ItemArtifactsItemContractPromoteRequestBuilder and sets the default values.
func NewItemArtifactsItemContractPromoteRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractPromoteRequestBuilder) {
    m := &ItemArtifactsItemContractPromoteRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/contract/promote", pathParameters),
    }
    return m
}
// NewItemArtifactsItemContractPromoteRequestBuilder instantiates a new ItemArtifactsItemContractPromoteRequestBuilder and sets the default values.
func NewItemArtifactsItemContractPromoteRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractPromoteRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemArtifactsItemContractPromoteRequestBuilderInternal(urlParams, requestAdapter)
}
// Post promotes a contract to the next deployment stage (DEV -> STAGE -> PROD).
// Deprecated: This method is obsolete. Use PostAsPromotePostResponse instead.
// returns a ItemArtifactsItemContractPromoteResponseable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractPromoteRequestBuilder) Post(ctx context.Context, body ItemArtifactsItemContractPromotePostRequestBodyable, requestConfiguration *ItemArtifactsItemContractPromoteRequestBuilderPostRequestConfiguration)(ItemArtifactsItemContractPromoteResponseable, error) {
    requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemContractPromoteResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemContractPromoteResponseable), nil
}
// PostAsPromotePostResponse promotes a contract to the next deployment stage (DEV -> STAGE -> PROD).
// returns a ItemArtifactsItemContractPromotePostResponseable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractPromoteRequestBuilder) PostAsPromotePostResponse(ctx context.Context, body ItemArtifactsItemContractPromotePostRequestBodyable, requestConfiguration *ItemArtifactsItemContractPromoteRequestBuilderPostRequestConfiguration)(ItemArtifactsItemContractPromotePostResponseable, error) {
    requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemContractPromotePostResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemContractPromotePostResponseable), nil
}
// ToPostRequestInformation promotes a contract to the next deployment stage (DEV -> STAGE -> PROD).
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemContractPromoteRequestBuilder) ToPostRequestInformation(ctx context.Context, body ItemArtifactsItemContractPromotePostRequestBodyable, requestConfiguration *ItemArtifactsItemContractPromoteRequestBuilderPostRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *ItemArtifactsItemContractPromoteRequestBuilder when successful
func (m *ItemArtifactsItemContractPromoteRequestBuilder) WithUrl(rawUrl string)(*ItemArtifactsItemContractPromoteRequestBuilder) {
    return NewItemArtifactsItemContractPromoteRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
