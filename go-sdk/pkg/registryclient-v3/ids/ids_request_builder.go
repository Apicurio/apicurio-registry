package ids

import (
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// IdsRequestBuilder builds and executes requests for operations under \ids
type IdsRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// NewIdsRequestBuilderInternal instantiates a new IdsRequestBuilder and sets the default values.
func NewIdsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*IdsRequestBuilder) {
    m := &IdsRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids", pathParameters),
    }
    return m
}
// NewIdsRequestBuilder instantiates a new IdsRequestBuilder and sets the default values.
func NewIdsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*IdsRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewIdsRequestBuilderInternal(urlParams, requestAdapter)
}
// ContentHashes the contentHashes property
func (m *IdsRequestBuilder) ContentHashes()(*ContentHashesRequestBuilder) {
    return NewContentHashesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// ContentIds the contentIds property
func (m *IdsRequestBuilder) ContentIds()(*ContentIdsRequestBuilder) {
    return NewContentIdsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// GlobalIds the globalIds property
func (m *IdsRequestBuilder) GlobalIds()(*GlobalIdsRequestBuilder) {
    return NewGlobalIdsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
