package admin

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder get deprecation readiness for a version.
type UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewUsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilderInternal instantiates a new UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder and sets the default values.
func NewUsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder {
	m := &UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/usage/artifacts/{groupId}/{artifactId}/versions/{version}/deprecation-readiness", pathParameters),
	}
	return m
}

// NewUsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder instantiates a new UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder and sets the default values.
func NewUsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewUsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a deprecation readiness report showing which consumers are still actively using this version and whether it is safe to deprecate.This operation can fail for the following reasons:* Usage telemetry is not enabled (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a DeprecationReadinessable when successful
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder) Get(ctx context.Context, requestConfiguration *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.DeprecationReadinessable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"409": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateDeprecationReadinessFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.DeprecationReadinessable), nil
}

// ToGetRequestInformation returns a deprecation readiness report showing which consumers are still actively using this version and whether it is safe to deprecate.This operation can fail for the following reasons:* Usage telemetry is not enabled (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder when successful
func (m *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder) WithUrl(rawUrl string) *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder {
	return NewUsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
