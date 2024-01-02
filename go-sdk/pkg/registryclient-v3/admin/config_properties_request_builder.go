package admin

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ConfigPropertiesRequestBuilder manage configuration properties.
type ConfigPropertiesRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ConfigPropertiesRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ConfigPropertiesRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ByPropertyName manage a single configuration property (by name).
func (m *ConfigPropertiesRequestBuilder) ByPropertyName(propertyName string) *ConfigPropertiesWithPropertyNameItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if propertyName != "" {
		urlTplParams["propertyName"] = propertyName
	}
	return NewConfigPropertiesWithPropertyNameItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewConfigPropertiesRequestBuilderInternal instantiates a new PropertiesRequestBuilder and sets the default values.
func NewConfigPropertiesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ConfigPropertiesRequestBuilder {
	m := &ConfigPropertiesRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/config/properties", pathParameters),
	}
	return m
}

// NewConfigPropertiesRequestBuilder instantiates a new PropertiesRequestBuilder and sets the default values.
func NewConfigPropertiesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ConfigPropertiesRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewConfigPropertiesRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a list of all configuration properties that have been set.  The list is not paged.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
func (m *ConfigPropertiesRequestBuilder) Get(ctx context.Context, requestConfiguration *ConfigPropertiesRequestBuilderGetRequestConfiguration) ([]i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ConfigurationPropertyable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateConfigurationPropertyFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	val := make([]i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ConfigurationPropertyable, len(res))
	for i, v := range res {
		if v != nil {
			val[i] = v.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ConfigurationPropertyable)
		}
	}
	return val, nil
}

// ToGetRequestInformation returns a list of all configuration properties that have been set.  The list is not paged.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
func (m *ConfigPropertiesRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ConfigPropertiesRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *ConfigPropertiesRequestBuilder) WithUrl(rawUrl string) *ConfigPropertiesRequestBuilder {
	return NewConfigPropertiesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
