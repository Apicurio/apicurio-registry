package admin

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// UsageArtifactsItemWithArtifactItemRequestBuilder get usage metrics for an artifact.
type UsageArtifactsItemWithArtifactItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// UsageArtifactsItemWithArtifactItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type UsageArtifactsItemWithArtifactItemRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewUsageArtifactsItemWithArtifactItemRequestBuilderInternal instantiates a new UsageArtifactsItemWithArtifactItemRequestBuilder and sets the default values.
func NewUsageArtifactsItemWithArtifactItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemWithArtifactItemRequestBuilder {
	m := &UsageArtifactsItemWithArtifactItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/usage/artifacts/{groupId}/{artifactId}", pathParameters),
	}
	return m
}

// NewUsageArtifactsItemWithArtifactItemRequestBuilder instantiates a new UsageArtifactsItemWithArtifactItemRequestBuilder and sets the default values.
func NewUsageArtifactsItemWithArtifactItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemWithArtifactItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewUsageArtifactsItemWithArtifactItemRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns aggregated usage metrics for all versions of the specified artifact, including fetch counts, client lists, and Active/Stale/Dead classification. This endpoint is only available when usage telemetry is enabled on the server.This operation can fail for the following reasons:* Usage telemetry is not enabled (HTTP error `409`)* No artifact with this name exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a ArtifactUsageMetricsable when successful
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *UsageArtifactsItemWithArtifactItemRequestBuilder) Get(ctx context.Context, requestConfiguration *UsageArtifactsItemWithArtifactItemRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactUsageMetricsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"409": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateArtifactUsageMetricsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactUsageMetricsable), nil
}

// Heatmap get consumer version heatmap for an artifact.
// returns a *UsageArtifactsItemItemHeatmapRequestBuilder when successful
func (m *UsageArtifactsItemWithArtifactItemRequestBuilder) Heatmap() *UsageArtifactsItemItemHeatmapRequestBuilder {
	return NewUsageArtifactsItemItemHeatmapRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// ToGetRequestInformation returns aggregated usage metrics for all versions of the specified artifact, including fetch counts, client lists, and Active/Stale/Dead classification. This endpoint is only available when usage telemetry is enabled on the server.This operation can fail for the following reasons:* Usage telemetry is not enabled (HTTP error `409`)* No artifact with this name exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *UsageArtifactsItemWithArtifactItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *UsageArtifactsItemWithArtifactItemRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// Versions the versions property
// returns a *UsageArtifactsItemItemVersionsRequestBuilder when successful
func (m *UsageArtifactsItemWithArtifactItemRequestBuilder) Versions() *UsageArtifactsItemItemVersionsRequestBuilder {
	return NewUsageArtifactsItemItemVersionsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *UsageArtifactsItemWithArtifactItemRequestBuilder when successful
func (m *UsageArtifactsItemWithArtifactItemRequestBuilder) WithUrl(rawUrl string) *UsageArtifactsItemWithArtifactItemRequestBuilder {
	return NewUsageArtifactsItemWithArtifactItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
