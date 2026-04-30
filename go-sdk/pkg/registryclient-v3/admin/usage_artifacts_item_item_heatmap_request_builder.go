package admin

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// UsageArtifactsItemItemHeatmapRequestBuilder get consumer version heatmap for an artifact.
type UsageArtifactsItemItemHeatmapRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// UsageArtifactsItemItemHeatmapRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type UsageArtifactsItemItemHeatmapRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewUsageArtifactsItemItemHeatmapRequestBuilderInternal instantiates a new UsageArtifactsItemItemHeatmapRequestBuilder and sets the default values.
func NewUsageArtifactsItemItemHeatmapRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemItemHeatmapRequestBuilder {
	m := &UsageArtifactsItemItemHeatmapRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/usage/artifacts/{groupId}/{artifactId}/heatmap", pathParameters),
	}
	return m
}

// NewUsageArtifactsItemItemHeatmapRequestBuilder instantiates a new UsageArtifactsItemItemHeatmapRequestBuilder and sets the default values.
func NewUsageArtifactsItemItemHeatmapRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemItemHeatmapRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewUsageArtifactsItemItemHeatmapRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a consumer version adoption heatmap showing which clients use which versions of the specified artifact, with version drift detection.This operation can fail for the following reasons:* Usage telemetry is not enabled (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a ConsumerVersionHeatmapable when successful
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *UsageArtifactsItemItemHeatmapRequestBuilder) Get(ctx context.Context, requestConfiguration *UsageArtifactsItemItemHeatmapRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ConsumerVersionHeatmapable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"409": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateConsumerVersionHeatmapFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ConsumerVersionHeatmapable), nil
}

// ToGetRequestInformation returns a consumer version adoption heatmap showing which clients use which versions of the specified artifact, with version drift detection.This operation can fail for the following reasons:* Usage telemetry is not enabled (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *UsageArtifactsItemItemHeatmapRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *UsageArtifactsItemItemHeatmapRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *UsageArtifactsItemItemHeatmapRequestBuilder when successful
func (m *UsageArtifactsItemItemHeatmapRequestBuilder) WithUrl(rawUrl string) *UsageArtifactsItemItemHeatmapRequestBuilder {
	return NewUsageArtifactsItemItemHeatmapRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
