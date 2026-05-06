package admin

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// UsageArtifactsWithGroupItemRequestBuilder builds and executes requests for operations under \admin\usage\artifacts\{groupId}
type UsageArtifactsWithGroupItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ByArtifactId get usage metrics for an artifact.
// returns a *UsageArtifactsItemWithArtifactItemRequestBuilder when successful
func (m *UsageArtifactsWithGroupItemRequestBuilder) ByArtifactId(artifactId string) *UsageArtifactsItemWithArtifactItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if artifactId != "" {
		urlTplParams["artifactId"] = artifactId
	}
	return NewUsageArtifactsItemWithArtifactItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewUsageArtifactsWithGroupItemRequestBuilderInternal instantiates a new UsageArtifactsWithGroupItemRequestBuilder and sets the default values.
func NewUsageArtifactsWithGroupItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsWithGroupItemRequestBuilder {
	m := &UsageArtifactsWithGroupItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/usage/artifacts/{groupId}", pathParameters),
	}
	return m
}

// NewUsageArtifactsWithGroupItemRequestBuilder instantiates a new UsageArtifactsWithGroupItemRequestBuilder and sets the default values.
func NewUsageArtifactsWithGroupItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsWithGroupItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewUsageArtifactsWithGroupItemRequestBuilderInternal(urlParams, requestAdapter)
}
