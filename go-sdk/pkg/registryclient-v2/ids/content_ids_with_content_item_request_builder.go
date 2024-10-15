package ids

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ContentIdsWithContentItemRequestBuilder builds and executes requests for operations under \ids\contentIds\{contentId}
type ContentIdsWithContentItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// NewContentIdsWithContentItemRequestBuilderInternal instantiates a new ContentIdsWithContentItemRequestBuilder and sets the default values.
func NewContentIdsWithContentItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentIdsWithContentItemRequestBuilder {
	m := &ContentIdsWithContentItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/contentIds/{contentId}", pathParameters),
	}
	return m
}

// NewContentIdsWithContentItemRequestBuilder instantiates a new ContentIdsWithContentItemRequestBuilder and sets the default values.
func NewContentIdsWithContentItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentIdsWithContentItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContentIdsWithContentItemRequestBuilderInternal(urlParams, requestAdapter)
}

// References the references property
// returns a *ContentIdsItemReferencesRequestBuilder when successful
func (m *ContentIdsWithContentItemRequestBuilder) References() *ContentIdsItemReferencesRequestBuilder {
	return NewContentIdsItemReferencesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
