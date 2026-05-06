package admin

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// GitopsStatusRequestBuilder get the current GitOps synchronization status.
type GitopsStatusRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// GitopsStatusRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type GitopsStatusRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewGitopsStatusRequestBuilderInternal instantiates a new GitopsStatusRequestBuilder and sets the default values.
func NewGitopsStatusRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GitopsStatusRequestBuilder {
	m := &GitopsStatusRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/gitops/status", pathParameters),
	}
	return m
}

// NewGitopsStatusRequestBuilder instantiates a new GitopsStatusRequestBuilder and sets the default values.
func NewGitopsStatusRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GitopsStatusRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewGitopsStatusRequestBuilderInternal(urlParams, requestAdapter)
}

// Get **Experimental.** Returns the current synchronization status of the GitOps storage, including the current commit SHA, sync state, load statistics, and any errors from the last sync attempt.This endpoint is only available when GitOps storage is enabled (`apicurio.storage.kind=gitops`). Returns HTTP 409 (Conflict) if a different storage backend is active.This operation can fail for the following reasons:* GitOps storage is not enabled (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a GitOpsStatusable when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *GitopsStatusRequestBuilder) Get(ctx context.Context, requestConfiguration *GitopsStatusRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.GitOpsStatusable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"409": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateGitOpsStatusFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.GitOpsStatusable), nil
}

// ToGetRequestInformation **Experimental.** Returns the current synchronization status of the GitOps storage, including the current commit SHA, sync state, load statistics, and any errors from the last sync attempt.This endpoint is only available when GitOps storage is enabled (`apicurio.storage.kind=gitops`). Returns HTTP 409 (Conflict) if a different storage backend is active.This operation can fail for the following reasons:* GitOps storage is not enabled (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *GitopsStatusRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *GitopsStatusRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *GitopsStatusRequestBuilder when successful
func (m *GitopsStatusRequestBuilder) WithUrl(rawUrl string) *GitopsStatusRequestBuilder {
	return NewGitopsStatusRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
