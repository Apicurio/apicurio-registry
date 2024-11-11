package ids

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ContentHashesWithContentHashItemRequestBuilder builds and executes requests for operations under \ids\contentHashes\{contentHash}
type ContentHashesWithContentHashItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// NewContentHashesWithContentHashItemRequestBuilderInternal instantiates a new ContentHashesWithContentHashItemRequestBuilder and sets the default values.
func NewContentHashesWithContentHashItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentHashesWithContentHashItemRequestBuilder {
	m := &ContentHashesWithContentHashItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/contentHashes/{contentHash}", pathParameters),
	}
	return m
}

// NewContentHashesWithContentHashItemRequestBuilder instantiates a new ContentHashesWithContentHashItemRequestBuilder and sets the default values.
func NewContentHashesWithContentHashItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentHashesWithContentHashItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContentHashesWithContentHashItemRequestBuilderInternal(urlParams, requestAdapter)
}

// References the references property
// returns a *ContentHashesItemReferencesRequestBuilder when successful
func (m *ContentHashesWithContentHashItemRequestBuilder) References() *ContentHashesItemReferencesRequestBuilder {
	return NewContentHashesItemReferencesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
