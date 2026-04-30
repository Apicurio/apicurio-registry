package admin

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder builds and executes requests for operations under \admin\usage\artifacts\{groupId}\{artifactId}\versions\{version}
type UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// NewUsageArtifactsItemItemVersionsWithVersionItemRequestBuilderInternal instantiates a new UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder and sets the default values.
func NewUsageArtifactsItemItemVersionsWithVersionItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder {
	m := &UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/usage/artifacts/{groupId}/{artifactId}/versions/{version}", pathParameters),
	}
	return m
}

// NewUsageArtifactsItemItemVersionsWithVersionItemRequestBuilder instantiates a new UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder and sets the default values.
func NewUsageArtifactsItemItemVersionsWithVersionItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewUsageArtifactsItemItemVersionsWithVersionItemRequestBuilderInternal(urlParams, requestAdapter)
}

// DeprecationReadiness get deprecation readiness for a version.
// returns a *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder when successful
func (m *UsageArtifactsItemItemVersionsWithVersionItemRequestBuilder) DeprecationReadiness() *UsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilder {
	return NewUsageArtifactsItemItemVersionsItemDeprecationReadinessRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
