package users

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/models"
)

// MeRequestBuilder retrieves information about the current user
type MeRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// MeRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type MeRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// NewMeRequestBuilderInternal instantiates a new MeRequestBuilder and sets the default values.
func NewMeRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*MeRequestBuilder) {
    m := &MeRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/users/me", pathParameters),
    }
    return m
}
// NewMeRequestBuilder instantiates a new MeRequestBuilder and sets the default values.
func NewMeRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*MeRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewMeRequestBuilderInternal(urlParams, requestAdapter)
}
// Get returns information about the currently authenticated user.
func (m *MeRequestBuilder) Get(ctx context.Context, requestConfiguration *MeRequestBuilderGetRequestConfiguration)(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.UserInfoable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateUserInfoFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.UserInfoable), nil
}
// ToGetRequestInformation returns information about the currently authenticated user.
func (m *MeRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *MeRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    return requestInfo, nil
}
// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *MeRequestBuilder) WithUrl(rawUrl string)(*MeRequestBuilder) {
    return NewMeRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
