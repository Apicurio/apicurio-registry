package admin

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// RoleMappingsRequestBuilder collection to manage role mappings for authenticated principals
type RoleMappingsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// RoleMappingsRequestBuilderGetQueryParameters gets a list of all role mappings configured in the registry (if any).This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
type RoleMappingsRequestBuilderGetQueryParameters struct {
	// The number of role mappings to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of role mappings to skip before starting the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
}

// RoleMappingsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type RoleMappingsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *RoleMappingsRequestBuilderGetQueryParameters
}

// RoleMappingsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type RoleMappingsRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ByPrincipalId manage the configuration of a single role mapping.
func (m *RoleMappingsRequestBuilder) ByPrincipalId(principalId string) *RoleMappingsWithPrincipalItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if principalId != "" {
		urlTplParams["principalId"] = principalId
	}
	return NewRoleMappingsWithPrincipalItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewRoleMappingsRequestBuilderInternal instantiates a new RoleMappingsRequestBuilder and sets the default values.
func NewRoleMappingsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *RoleMappingsRequestBuilder {
	m := &RoleMappingsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/roleMappings{?limit*,offset*}", pathParameters),
	}
	return m
}

// NewRoleMappingsRequestBuilder instantiates a new RoleMappingsRequestBuilder and sets the default values.
func NewRoleMappingsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *RoleMappingsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewRoleMappingsRequestBuilderInternal(urlParams, requestAdapter)
}

// Get gets a list of all role mappings configured in the registry (if any).This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
func (m *RoleMappingsRequestBuilder) Get(ctx context.Context, requestConfiguration *RoleMappingsRequestBuilderGetRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.RoleMappingSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateRoleMappingSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.RoleMappingSearchResultsable), nil
}

// Post creates a new mapping between a user/principal and a role.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
func (m *RoleMappingsRequestBuilder) Post(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.RoleMappingable, requestConfiguration *RoleMappingsRequestBuilderPostRequestConfiguration) error {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// ToGetRequestInformation gets a list of all role mappings configured in the registry (if any).This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
func (m *RoleMappingsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *RoleMappingsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		if requestConfiguration.QueryParameters != nil {
			requestInfo.AddQueryParameters(*(requestConfiguration.QueryParameters))
		}
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToPostRequestInformation creates a new mapping between a user/principal and a role.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
func (m *RoleMappingsRequestBuilder) ToPostRequestInformation(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.RoleMappingable, requestConfiguration *RoleMappingsRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	err := requestInfo.SetContentFromParsable(ctx, m.BaseRequestBuilder.RequestAdapter, "application/json", body)
	if err != nil {
		return nil, err
	}
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *RoleMappingsRequestBuilder) WithUrl(rawUrl string) *RoleMappingsRequestBuilder {
	return NewRoleMappingsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
