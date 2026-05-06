package admin

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// UsageArtifactsRequestBuilder builds and executes requests for operations under \admin\usage\artifacts
type UsageArtifactsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ByGroupId gets an item from the github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3.admin.usage.artifacts.item collection
// returns a *UsageArtifactsWithGroupItemRequestBuilder when successful
func (m *UsageArtifactsRequestBuilder) ByGroupId(groupId string) *UsageArtifactsWithGroupItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if groupId != "" {
		urlTplParams["groupId"] = groupId
	}
	return NewUsageArtifactsWithGroupItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewUsageArtifactsRequestBuilderInternal instantiates a new UsageArtifactsRequestBuilder and sets the default values.
func NewUsageArtifactsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsRequestBuilder {
	m := &UsageArtifactsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/usage/artifacts", pathParameters),
	}
	return m
}

// NewUsageArtifactsRequestBuilder instantiates a new UsageArtifactsRequestBuilder and sets the default values.
func NewUsageArtifactsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageArtifactsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewUsageArtifactsRequestBuilderInternal(urlParams, requestAdapter)
}
