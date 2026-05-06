package admin

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// UsageArtifactsItemItemVersionsRequestBuilder builds and executes requests for operations under \admin\usage\artifacts\{groupId}\{artifactId}\versions
type UsageArtifactsItemItemVersionsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ByVersion gets an item from the github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3.admin.usage.artifacts.item.item.versions.item collection
// returns a *UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder when successful
func (m *UsageArtifactsItemItemVersionsRequestBuilder) ByVersion(version string) *UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if version != "" {
		urlTplParams["version"] = version
	}
	return NewUsageArtifactsItemItemVersionsWithVersionItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewUsageArtifactsItemItemVersionsRequestBuilderInternal instantiates a new UsageArtifactsItemItemVersionsRequestBuilder and sets the default values.
func NewUsageArtifactsItemItemVersionsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemItemVersionsRequestBuilder {
	m := &UsageArtifactsItemItemVersionsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/usage/artifacts/{groupId}/{artifactId}/versions", pathParameters),
	}
	return m
}

// NewUsageArtifactsItemItemVersionsRequestBuilder instantiates a new UsageArtifactsItemItemVersionsRequestBuilder and sets the default values.
func NewUsageArtifactsItemItemVersionsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemItemVersionsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewUsageArtifactsItemItemVersionsRequestBuilderInternal(urlParams, requestAdapter)
}
