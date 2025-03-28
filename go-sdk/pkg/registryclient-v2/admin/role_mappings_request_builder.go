package admin

import (
	"context"
	idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// RoleMappingsRequestBuilder collection to manage role mappings for authenticated principals
type RoleMappingsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// RoleMappingsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type RoleMappingsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// RoleMappingsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type RoleMappingsRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ByPrincipalId manage the configuration of a single role mapping.
// returns a *RoleMappingsWithPrincipalItemRequestBuilder when successful
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
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/roleMappings", pathParameters),
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
// returns a []RoleMappingable when successful
// returns a Error error when the service returns a 500 status code
func (m *RoleMappingsRequestBuilder) Get(ctx context.Context, requestConfiguration *RoleMappingsRequestBuilderGetRequestConfiguration) ([]idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.RoleMappingable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateRoleMappingFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	val := make([]idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.RoleMappingable, len(res))
	for i, v := range res {
		if v != nil {
			val[i] = v.(idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.RoleMappingable)
		}
	}
	return val, nil
}

// Post creates a new mapping between a user/principal and a role.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a Error error when the service returns a 500 status code
func (m *RoleMappingsRequestBuilder) Post(ctx context.Context, body idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.RoleMappingable, requestConfiguration *RoleMappingsRequestBuilderPostRequestConfiguration) error {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// ToGetRequestInformation gets a list of all role mappings configured in the registry (if any).This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *RoleMappingsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *RoleMappingsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToPostRequestInformation creates a new mapping between a user/principal and a role.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *RoleMappingsRequestBuilder) ToPostRequestInformation(ctx context.Context, body idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.RoleMappingable, requestConfiguration *RoleMappingsRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *RoleMappingsRequestBuilder when successful
func (m *RoleMappingsRequestBuilder) WithUrl(rawUrl string) *RoleMappingsRequestBuilder {
	return NewRoleMappingsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
