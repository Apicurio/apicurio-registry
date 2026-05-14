package content

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ContentRequestBuilder builds and executes requests for operations under \content
type ContentRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// NewContentRequestBuilderInternal instantiates a new ContentRequestBuilder and sets the default values.
func NewContentRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentRequestBuilder {
	m := &ContentRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/content", pathParameters),
	}
	return m
}

// NewContentRequestBuilder instantiates a new ContentRequestBuilder and sets the default values.
func NewContentRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContentRequestBuilderInternal(urlParams, requestAdapter)
}

// References detect external references in artifact content.
// returns a *ReferencesRequestBuilder when successful
func (m *ContentRequestBuilder) References() *ReferencesRequestBuilder {
	return NewReferencesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
