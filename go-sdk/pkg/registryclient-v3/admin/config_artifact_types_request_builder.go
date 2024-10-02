package admin

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ConfigArtifactTypesRequestBuilder the list of artifact types supported by this instance of Registry.
type ConfigArtifactTypesRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ConfigArtifactTypesRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ConfigArtifactTypesRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewConfigArtifactTypesRequestBuilderInternal instantiates a new ConfigArtifactTypesRequestBuilder and sets the default values.
func NewConfigArtifactTypesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ConfigArtifactTypesRequestBuilder {
	m := &ConfigArtifactTypesRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/config/artifactTypes", pathParameters),
	}
	return m
}

// NewConfigArtifactTypesRequestBuilder instantiates a new ConfigArtifactTypesRequestBuilder and sets the default values.
func NewConfigArtifactTypesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ConfigArtifactTypesRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewConfigArtifactTypesRequestBuilderInternal(urlParams, requestAdapter)
}

// Get gets a list of all the configured artifact types.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a []ArtifactTypeInfoable when successful
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ConfigArtifactTypesRequestBuilder) Get(ctx context.Context, requestConfiguration *ConfigArtifactTypesRequestBuilderGetRequestConfiguration) ([]i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactTypeInfoable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactTypeInfoFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	val := make([]i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactTypeInfoable, len(res))
	for i, v := range res {
		if v != nil {
			val[i] = v.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactTypeInfoable)
		}
	}
	return val, nil
}

// ToGetRequestInformation gets a list of all the configured artifact types.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ConfigArtifactTypesRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ConfigArtifactTypesRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ConfigArtifactTypesRequestBuilder when successful
func (m *ConfigArtifactTypesRequestBuilder) WithUrl(rawUrl string) *ConfigArtifactTypesRequestBuilder {
	return NewConfigArtifactTypesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
