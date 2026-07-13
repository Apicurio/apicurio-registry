package groups

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
)

// ItemArtifactsItemContractMigrateRequestBuilder execute migration rules to transform a record between versions.
type ItemArtifactsItemContractMigrateRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ItemArtifactsItemContractMigrateRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemContractMigrateRequestBuilderPostRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// NewItemArtifactsItemContractMigrateRequestBuilderInternal instantiates a new ItemArtifactsItemContractMigrateRequestBuilder and sets the default values.
func NewItemArtifactsItemContractMigrateRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractMigrateRequestBuilder) {
    m := &ItemArtifactsItemContractMigrateRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/contract/migrate", pathParameters),
    }
    return m
}
// NewItemArtifactsItemContractMigrateRequestBuilder instantiates a new ItemArtifactsItemContractMigrateRequestBuilder and sets the default values.
func NewItemArtifactsItemContractMigrateRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemContractMigrateRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemArtifactsItemContractMigrateRequestBuilderInternal(urlParams, requestAdapter)
}
// Post executes migration rules to transform a record from one schema version to another, chaining transforms across version hops.
// Deprecated: This method is obsolete. Use PostAsMigratePostResponse instead.
// returns a ItemArtifactsItemContractMigrateResponseable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractMigrateRequestBuilder) Post(ctx context.Context, body ItemArtifactsItemContractMigratePostRequestBodyable, requestConfiguration *ItemArtifactsItemContractMigrateRequestBuilderPostRequestConfiguration)(ItemArtifactsItemContractMigrateResponseable, error) {
    requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemContractMigrateResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemContractMigrateResponseable), nil
}
// PostAsMigratePostResponse executes migration rules to transform a record from one schema version to another, chaining transforms across version hops.
// returns a ItemArtifactsItemContractMigratePostResponseable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractMigrateRequestBuilder) PostAsMigratePostResponse(ctx context.Context, body ItemArtifactsItemContractMigratePostRequestBodyable, requestConfiguration *ItemArtifactsItemContractMigrateRequestBuilderPostRequestConfiguration)(ItemArtifactsItemContractMigratePostResponseable, error) {
    requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, CreateItemArtifactsItemContractMigratePostResponseFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(ItemArtifactsItemContractMigratePostResponseable), nil
}
// ToPostRequestInformation executes migration rules to transform a record from one schema version to another, chaining transforms across version hops.
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemContractMigrateRequestBuilder) ToPostRequestInformation(ctx context.Context, body ItemArtifactsItemContractMigratePostRequestBodyable, requestConfiguration *ItemArtifactsItemContractMigrateRequestBuilderPostRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *ItemArtifactsItemContractMigrateRequestBuilder when successful
func (m *ItemArtifactsItemContractMigrateRequestBuilder) WithUrl(rawUrl string)(*ItemArtifactsItemContractMigrateRequestBuilder) {
    return NewItemArtifactsItemContractMigrateRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
