package ids

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ContentHashesRequestBuilder builds and executes requests for operations under \ids\contentHashes
type ContentHashesRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ByContentHash gets an item from the github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2.ids.contentHashes.item collection
// returns a *ContentHashesWithContentHashItemRequestBuilder when successful
func (m *ContentHashesRequestBuilder) ByContentHash(contentHash string) *ContentHashesWithContentHashItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if contentHash != "" {
		urlTplParams["contentHash"] = contentHash
	}
	return NewContentHashesWithContentHashItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewContentHashesRequestBuilderInternal instantiates a new ContentHashesRequestBuilder and sets the default values.
func NewContentHashesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentHashesRequestBuilder {
	m := &ContentHashesRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/contentHashes", pathParameters),
	}
	return m
}

// NewContentHashesRequestBuilder instantiates a new ContentHashesRequestBuilder and sets the default values.
func NewContentHashesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentHashesRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContentHashesRequestBuilderInternal(urlParams, requestAdapter)
}

// WithContentHash access artifact content utilizing the SHA-256 hash of the content.
// returns a *ContentHashesItemWithContentHashRequestBuilder when successful
func (m *ContentHashesRequestBuilder) WithContentHash(contentHash *string) *ContentHashesItemWithContentHashRequestBuilder {
	return NewContentHashesItemWithContentHashRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter, contentHash)
}
