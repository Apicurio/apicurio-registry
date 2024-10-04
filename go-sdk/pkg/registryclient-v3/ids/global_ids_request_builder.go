package ids

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
	i53ac87e8cb3cc9276228f74d38694a208cacb99bb8ceb705eeae99fb88d4d274 "strconv"
)

// GlobalIdsRequestBuilder builds and executes requests for operations under \ids\globalIds
type GlobalIdsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ByGlobalId access artifact content utilizing an artifact version's globally unique identifier.
// Deprecated: This indexer is deprecated and will be removed in the next major version. Use the one with the typed parameter instead.
func (m *GlobalIdsRequestBuilder) ByGlobalId(globalId string) *GlobalIdsWithGlobalItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if globalId != "" {
		urlTplParams["globalId"] = globalId
	}
	return NewGlobalIdsWithGlobalItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// ByGlobalIdInt64 access artifact content utilizing an artifact version's globally unique identifier.
func (m *GlobalIdsRequestBuilder) ByGlobalIdInt64(globalId int64) *GlobalIdsWithGlobalItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	urlTplParams["globalId"] = i53ac87e8cb3cc9276228f74d38694a208cacb99bb8ceb705eeae99fb88d4d274.FormatInt(globalId, 10)
	return NewGlobalIdsWithGlobalItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewGlobalIdsRequestBuilderInternal instantiates a new GlobalIdsRequestBuilder and sets the default values.
func NewGlobalIdsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GlobalIdsRequestBuilder {
	m := &GlobalIdsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/globalIds", pathParameters),
	}
	return m
}

// NewGlobalIdsRequestBuilder instantiates a new GlobalIdsRequestBuilder and sets the default values.
func NewGlobalIdsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GlobalIdsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewGlobalIdsRequestBuilderInternal(urlParams, requestAdapter)
}
