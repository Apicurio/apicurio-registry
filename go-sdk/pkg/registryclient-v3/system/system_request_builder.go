package system

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// SystemRequestBuilder builds and executes requests for operations under \system
type SystemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// NewSystemRequestBuilderInternal instantiates a new SystemRequestBuilder and sets the default values.
func NewSystemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *SystemRequestBuilder {
	m := &SystemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/system", pathParameters),
	}
	return m
}

// NewSystemRequestBuilder instantiates a new SystemRequestBuilder and sets the default values.
func NewSystemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *SystemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewSystemRequestBuilderInternal(urlParams, requestAdapter)
}

// Info retrieve system information
func (m *SystemRequestBuilder) Info() *InfoRequestBuilder {
	return NewInfoRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Limits retrieve resource limits information
func (m *SystemRequestBuilder) Limits() *LimitsRequestBuilder {
	return NewLimitsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// UiConfig this endpoint is used by the user interface to retrieve UI specific configurationin a JSON payload.  This allows the UI and the backend to be configured in the same place (the backend process/pod).  When the UI loads, it will make an API callto this endpoint to determine what UI features and options are configured.
func (m *SystemRequestBuilder) UiConfig() *UiConfigRequestBuilder {
	return NewUiConfigRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
