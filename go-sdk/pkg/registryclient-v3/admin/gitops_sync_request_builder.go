package admin

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// GitopsSyncRequestBuilder trigger an immediate GitOps synchronization.
type GitopsSyncRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// GitopsSyncRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type GitopsSyncRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewGitopsSyncRequestBuilderInternal instantiates a new GitopsSyncRequestBuilder and sets the default values.
func NewGitopsSyncRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GitopsSyncRequestBuilder {
	m := &GitopsSyncRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/gitops/sync", pathParameters),
	}
	return m
}

// NewGitopsSyncRequestBuilder instantiates a new GitopsSyncRequestBuilder and sets the default values.
func NewGitopsSyncRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GitopsSyncRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewGitopsSyncRequestBuilderInternal(urlParams, requestAdapter)
}

// Post **Experimental.** Requests an immediate synchronization of the GitOps storage. This resets the poll timer so the next scheduler cycle will poll the Git repository without waiting for the configured poll period.The synchronization happens asynchronously — this endpoint returns immediately and the actual sync occurs on the next scheduler cycle.This endpoint is only available when GitOps storage is enabled (`apicurio.storage.kind=gitops`). Returns HTTP 409 (Conflict) if a different storage backend is active.This operation can fail for the following reasons:* GitOps storage is not enabled (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *GitopsSyncRequestBuilder) Post(ctx context.Context, requestConfiguration *GitopsSyncRequestBuilderPostRequestConfiguration) error {
	requestInfo, err := m.ToPostRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"409": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// ToPostRequestInformation **Experimental.** Requests an immediate synchronization of the GitOps storage. This resets the poll timer so the next scheduler cycle will poll the Git repository without waiting for the configured poll period.The synchronization happens asynchronously — this endpoint returns immediately and the actual sync occurs on the next scheduler cycle.This endpoint is only available when GitOps storage is enabled (`apicurio.storage.kind=gitops`). Returns HTTP 409 (Conflict) if a different storage backend is active.This operation can fail for the following reasons:* GitOps storage is not enabled (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *GitopsSyncRequestBuilder) ToPostRequestInformation(ctx context.Context, requestConfiguration *GitopsSyncRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *GitopsSyncRequestBuilder when successful
func (m *GitopsSyncRequestBuilder) WithUrl(rawUrl string) *GitopsSyncRequestBuilder {
	return NewGitopsSyncRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
