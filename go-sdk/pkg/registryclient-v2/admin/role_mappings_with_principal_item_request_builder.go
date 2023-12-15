package admin

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/models"
)

// RoleMappingsWithPrincipalItemRequestBuilder manage the configuration of a single role mapping.
type RoleMappingsWithPrincipalItemRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// RoleMappingsWithPrincipalItemRequestBuilderDeleteRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type RoleMappingsWithPrincipalItemRequestBuilderDeleteRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// RoleMappingsWithPrincipalItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type RoleMappingsWithPrincipalItemRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// RoleMappingsWithPrincipalItemRequestBuilderPutRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type RoleMappingsWithPrincipalItemRequestBuilderPutRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// NewRoleMappingsWithPrincipalItemRequestBuilderInternal instantiates a new WithPrincipalItemRequestBuilder and sets the default values.
func NewRoleMappingsWithPrincipalItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*RoleMappingsWithPrincipalItemRequestBuilder) {
    m := &RoleMappingsWithPrincipalItemRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/roleMappings/{principalId}", pathParameters),
    }
    return m
}
// NewRoleMappingsWithPrincipalItemRequestBuilder instantiates a new WithPrincipalItemRequestBuilder and sets the default values.
func NewRoleMappingsWithPrincipalItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*RoleMappingsWithPrincipalItemRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewRoleMappingsWithPrincipalItemRequestBuilderInternal(urlParams, requestAdapter)
}
// Delete deletes a single role mapping, effectively denying access to a user/principal.This operation can fail for the following reasons:* No role mapping for the principalId exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *RoleMappingsWithPrincipalItemRequestBuilder) Delete(ctx context.Context, requestConfiguration *RoleMappingsWithPrincipalItemRequestBuilderDeleteRequestConfiguration)(error) {
    requestInfo, err := m.ToDeleteRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
        "500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
    }
    err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
    if err != nil {
        return err
    }
    return nil
}
// Get gets the details of a single role mapping (by `principalId`).This operation can fail for the following reasons:* No role mapping for the `principalId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *RoleMappingsWithPrincipalItemRequestBuilder) Get(ctx context.Context, requestConfiguration *RoleMappingsWithPrincipalItemRequestBuilderGetRequestConfiguration)(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.RoleMappingable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
        "500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateRoleMappingFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.RoleMappingable), nil
}
// Put updates a single role mapping for one user/principal.This operation can fail for the following reasons:* No role mapping for the principalId exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *RoleMappingsWithPrincipalItemRequestBuilder) Put(ctx context.Context, body i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.UpdateRoleable, requestConfiguration *RoleMappingsWithPrincipalItemRequestBuilderPutRequestConfiguration)(error) {
    requestInfo, err := m.ToPutRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
        "500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
    }
    err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
    if err != nil {
        return err
    }
    return nil
}
// ToDeleteRequestInformation deletes a single role mapping, effectively denying access to a user/principal.This operation can fail for the following reasons:* No role mapping for the principalId exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *RoleMappingsWithPrincipalItemRequestBuilder) ToDeleteRequestInformation(ctx context.Context, requestConfiguration *RoleMappingsWithPrincipalItemRequestBuilderDeleteRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.DELETE, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    return requestInfo, nil
}
// ToGetRequestInformation gets the details of a single role mapping (by `principalId`).This operation can fail for the following reasons:* No role mapping for the `principalId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *RoleMappingsWithPrincipalItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *RoleMappingsWithPrincipalItemRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    return requestInfo, nil
}
// ToPutRequestInformation updates a single role mapping for one user/principal.This operation can fail for the following reasons:* No role mapping for the principalId exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *RoleMappingsWithPrincipalItemRequestBuilder) ToPutRequestInformation(ctx context.Context, body i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.UpdateRoleable, requestConfiguration *RoleMappingsWithPrincipalItemRequestBuilderPutRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.PUT, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
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
func (m *RoleMappingsWithPrincipalItemRequestBuilder) WithUrl(rawUrl string)(*RoleMappingsWithPrincipalItemRequestBuilder) {
    return NewRoleMappingsWithPrincipalItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
