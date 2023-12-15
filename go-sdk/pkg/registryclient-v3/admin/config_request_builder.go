package admin

import (
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ConfigRequestBuilder builds and executes requests for operations under \admin\config
type ConfigRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// NewConfigRequestBuilderInternal instantiates a new ConfigRequestBuilder and sets the default values.
func NewConfigRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ConfigRequestBuilder) {
    m := &ConfigRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/config", pathParameters),
    }
    return m
}
// NewConfigRequestBuilder instantiates a new ConfigRequestBuilder and sets the default values.
func NewConfigRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ConfigRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewConfigRequestBuilderInternal(urlParams, requestAdapter)
}
// Properties manage configuration properties.
func (m *ConfigRequestBuilder) Properties()(*ConfigPropertiesRequestBuilder) {
    return NewConfigPropertiesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
