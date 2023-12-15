package admin

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
)

// ArtifactTypesRequestBuilder the list of artifact types supported by this instance of Registry.
type ArtifactTypesRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ArtifactTypesRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ArtifactTypesRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// NewArtifactTypesRequestBuilderInternal instantiates a new ArtifactTypesRequestBuilder and sets the default values.
func NewArtifactTypesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ArtifactTypesRequestBuilder) {
    m := &ArtifactTypesRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/artifactTypes", pathParameters),
    }
    return m
}
// NewArtifactTypesRequestBuilder instantiates a new ArtifactTypesRequestBuilder and sets the default values.
func NewArtifactTypesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ArtifactTypesRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewArtifactTypesRequestBuilderInternal(urlParams, requestAdapter)
}
// Get gets a list of all the configured artifact types.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
func (m *ArtifactTypesRequestBuilder) Get(ctx context.Context, requestConfiguration *ArtifactTypesRequestBuilderGetRequestConfiguration)([]i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactTypeInfoable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
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
func (m *ArtifactTypesRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ArtifactTypesRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    return requestInfo, nil
}
// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *ArtifactTypesRequestBuilder) WithUrl(rawUrl string)(*ArtifactTypesRequestBuilder) {
    return NewArtifactTypesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
