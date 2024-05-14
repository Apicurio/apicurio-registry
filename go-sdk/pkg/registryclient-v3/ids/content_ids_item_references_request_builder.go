package ids

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
)

// ContentIdsItemReferencesRequestBuilder builds and executes requests for operations under \ids\contentIds\{contentId}\references
type ContentIdsItemReferencesRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ContentIdsItemReferencesRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ContentIdsItemReferencesRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// NewContentIdsItemReferencesRequestBuilderInternal instantiates a new ReferencesRequestBuilder and sets the default values.
func NewContentIdsItemReferencesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ContentIdsItemReferencesRequestBuilder) {
    m := &ContentIdsItemReferencesRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/contentIds/{contentId}/references", pathParameters),
    }
    return m
}
// NewContentIdsItemReferencesRequestBuilder instantiates a new ReferencesRequestBuilder and sets the default values.
func NewContentIdsItemReferencesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ContentIdsItemReferencesRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewContentIdsItemReferencesRequestBuilderInternal(urlParams, requestAdapter)
}
// Get returns a list containing all the artifact references using the artifact content ID.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
func (m *ContentIdsItemReferencesRequestBuilder) Get(ctx context.Context, requestConfiguration *ContentIdsItemReferencesRequestBuilderGetRequestConfiguration)([]i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactReferenceable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactReferenceFromDiscriminatorValue, nil)
    if err != nil {
        return nil, err
    }
    val := make([]i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactReferenceable, len(res))
    for i, v := range res {
        if v != nil {
            val[i] = v.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactReferenceable)
        }
    }
    return val, nil
}
// ToGetRequestInformation returns a list containing all the artifact references using the artifact content ID.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
func (m *ContentIdsItemReferencesRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ContentIdsItemReferencesRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    return requestInfo, nil
}
// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *ContentIdsItemReferencesRequestBuilder) WithUrl(rawUrl string)(*ContentIdsItemReferencesRequestBuilder) {
    return NewContentIdsItemReferencesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
