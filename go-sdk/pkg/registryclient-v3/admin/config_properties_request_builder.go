package admin

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
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
// returns a *ConfigPropertiesWithPropertyNameItemRequestBuilder when successful
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

// NewConfigPropertiesRequestBuilderInternal instantiates a new ConfigPropertiesRequestBuilder and sets the default values.
func NewConfigPropertiesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ConfigPropertiesRequestBuilder {
	m := &ConfigPropertiesRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/config/properties", pathParameters),
	}
	return m
}

// NewConfigPropertiesRequestBuilder instantiates a new ConfigPropertiesRequestBuilder and sets the default values.
func NewConfigPropertiesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ConfigPropertiesRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewConfigPropertiesRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a list of all configuration properties that have been set.  The list is not paged.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
// returns a []ConfigurationPropertyable when successful
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ConfigPropertiesRequestBuilder) Get(ctx context.Context, requestConfiguration *ConfigPropertiesRequestBuilderGetRequestConfiguration) ([]iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ConfigurationPropertyable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateConfigurationPropertyFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	val := make([]iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ConfigurationPropertyable, len(res))
	for i, v := range res {
		if v != nil {
			val[i] = v.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ConfigurationPropertyable)
		}
	}
	return val, nil
}

// ToGetRequestInformation returns a list of all configuration properties that have been set.  The list is not paged.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
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
// returns a *ConfigPropertiesRequestBuilder when successful
func (m *ConfigPropertiesRequestBuilder) WithUrl(rawUrl string) *ConfigPropertiesRequestBuilder {
	return NewConfigPropertiesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
