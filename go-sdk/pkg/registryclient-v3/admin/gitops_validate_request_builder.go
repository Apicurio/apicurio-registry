package admin

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
)

// GitopsValidateRequestBuilder manage dry-run validation tasks.
type GitopsValidateRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// GitopsValidateRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type GitopsValidateRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// GitopsValidateRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type GitopsValidateRequestBuilderPostRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// ByTaskId manage a specific validation task.
// returns a *GitopsValidateWithTaskItemRequestBuilder when successful
func (m *GitopsValidateRequestBuilder) ByTaskId(taskId string)(*GitopsValidateWithTaskItemRequestBuilder) {
    urlTplParams := make(map[string]string)
    for idx, item := range m.BaseRequestBuilder.PathParameters {
        urlTplParams[idx] = item
    }
    if taskId != "" {
        urlTplParams["taskId"] = taskId
    }
    return NewGitopsValidateWithTaskItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}
// NewGitopsValidateRequestBuilderInternal instantiates a new GitopsValidateRequestBuilder and sets the default values.
func NewGitopsValidateRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*GitopsValidateRequestBuilder) {
    m := &GitopsValidateRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/gitops/validate", pathParameters),
    }
    return m
}
// NewGitopsValidateRequestBuilder instantiates a new GitopsValidateRequestBuilder and sets the default values.
func NewGitopsValidateRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*GitopsValidateRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewGitopsValidateRequestBuilderInternal(urlParams, requestAdapter)
}
// Get **Experimental.** Returns a list of all active dry-run validation tasks.
// returns a []GitOpsValidateTaskable when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *GitopsValidateRequestBuilder) Get(ctx context.Context, requestConfiguration *GitopsValidateRequestBuilderGetRequestConfiguration)([]iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.GitOpsValidateTaskable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "409": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateGitOpsValidateTaskFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    val := make([]iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.GitOpsValidateTaskable, len(res))
    for i, v := range res {
        if v != nil {
            val[i] = v.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.GitOpsValidateTaskable)
        }
    }
    return val, nil
}
// Post **Experimental.** Creates a dry-run validation task that fetches a git ref and validates the data without affecting the live registry. The sidecar fetches the ref, and the registry loads and validates the data in the inactive storage (no swap).Returns a task object with a `taskId` that can be used to poll for results.This endpoint is only available when GitOps storage is enabled.This operation can fail for the following reasons:* GitOps storage is not enabled (HTTP error `409`)* Dry-run validation is disabled (HTTP error `503`)* A server error occurred (HTTP error `500`)
// returns a GitOpsValidateTaskable when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *GitopsValidateRequestBuilder) Post(ctx context.Context, body iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.GitOpsValidateRequestable, requestConfiguration *GitopsValidateRequestBuilderPostRequestConfiguration)(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.GitOpsValidateTaskable, error) {
    requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "409": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateGitOpsValidateTaskFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.GitOpsValidateTaskable), nil
}
// ToGetRequestInformation **Experimental.** Returns a list of all active dry-run validation tasks.
// returns a *RequestInformation when successful
func (m *GitopsValidateRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *GitopsValidateRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    return requestInfo, nil
}
// ToPostRequestInformation **Experimental.** Creates a dry-run validation task that fetches a git ref and validates the data without affecting the live registry. The sidecar fetches the ref, and the registry loads and validates the data in the inactive storage (no swap).Returns a task object with a `taskId` that can be used to poll for results.This endpoint is only available when GitOps storage is enabled.This operation can fail for the following reasons:* GitOps storage is not enabled (HTTP error `409`)* Dry-run validation is disabled (HTTP error `503`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *GitopsValidateRequestBuilder) ToPostRequestInformation(ctx context.Context, body iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.GitOpsValidateRequestable, requestConfiguration *GitopsValidateRequestBuilderPostRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *GitopsValidateRequestBuilder when successful
func (m *GitopsValidateRequestBuilder) WithUrl(rawUrl string)(*GitopsValidateRequestBuilder) {
    return NewGitopsValidateRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
