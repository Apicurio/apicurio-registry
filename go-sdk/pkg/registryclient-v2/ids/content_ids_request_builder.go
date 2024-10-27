package ids

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
	i53ac87e8cb3cc9276228f74d38694a208cacb99bb8ceb705eeae99fb88d4d274 "strconv"
)

// ContentIdsRequestBuilder builds and executes requests for operations under \ids\contentIds
type ContentIdsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ByContentId gets an item from the github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2.ids.contentIds.item collection
// Deprecated: This indexer is deprecated and will be removed in the next major version. Use the one with the typed parameter instead.
// returns a *ContentIdsWithContentItemRequestBuilder when successful
func (m *ContentIdsRequestBuilder) ByContentId(contentId string) *ContentIdsWithContentItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if contentId != "" {
		urlTplParams["contentId"] = contentId
	}
	return NewContentIdsWithContentItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// ByContentIdInt64 gets an item from the github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2.ids.contentIds.item collection
// returns a *ContentIdsWithContentItemRequestBuilder when successful
func (m *ContentIdsRequestBuilder) ByContentIdInt64(contentId int64) *ContentIdsWithContentItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	urlTplParams["contentId"] = i53ac87e8cb3cc9276228f74d38694a208cacb99bb8ceb705eeae99fb88d4d274.FormatInt(contentId, 10)
	return NewContentIdsWithContentItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewContentIdsRequestBuilderInternal instantiates a new ContentIdsRequestBuilder and sets the default values.
func NewContentIdsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentIdsRequestBuilder {
	m := &ContentIdsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/contentIds", pathParameters),
	}
	return m
}

// NewContentIdsRequestBuilder instantiates a new ContentIdsRequestBuilder and sets the default values.
func NewContentIdsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentIdsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContentIdsRequestBuilderInternal(urlParams, requestAdapter)
}

// WithContentId access artifact content utilizing the unique content identifier for that content.
// returns a *ContentIdsItemWithContentIdRequestBuilder when successful
func (m *ContentIdsRequestBuilder) WithContentId(contentId *int64) *ContentIdsItemWithContentIdRequestBuilder {
	return NewContentIdsItemWithContentIdRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter, contentId)
}
