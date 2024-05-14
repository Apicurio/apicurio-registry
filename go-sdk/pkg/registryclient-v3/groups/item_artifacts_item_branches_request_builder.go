package groups

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
)

// ItemArtifactsItemBranchesRequestBuilder manage branches of an artifact.
type ItemArtifactsItemBranchesRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ItemArtifactsItemBranchesRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemBranchesRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// ByBranchId manage a single artifact branch.
func (m *ItemArtifactsItemBranchesRequestBuilder) ByBranchId(branchId string)(*ItemArtifactsItemBranchesWithBranchItemRequestBuilder) {
    urlTplParams := make(map[string]string)
    for idx, item := range m.BaseRequestBuilder.PathParameters {
        urlTplParams[idx] = item
    }
    if branchId != "" {
        urlTplParams["branchId"] = branchId
    }
    return NewItemArtifactsItemBranchesWithBranchItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}
// NewItemArtifactsItemBranchesRequestBuilderInternal instantiates a new BranchesRequestBuilder and sets the default values.
func NewItemArtifactsItemBranchesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemBranchesRequestBuilder) {
    m := &ItemArtifactsItemBranchesRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/branches", pathParameters),
    }
    return m
}
// NewItemArtifactsItemBranchesRequestBuilder instantiates a new BranchesRequestBuilder and sets the default values.
func NewItemArtifactsItemBranchesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemBranchesRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemArtifactsItemBranchesRequestBuilderInternal(urlParams, requestAdapter)
}
// Get returns a list of all branches in the artifact. Each branch is a list of version identifiers,ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemBranchesRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemBranchesRequestBuilderGetRequestConfiguration)([]i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
        "500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactBranchFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    val := make([]i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable, len(res))
    for i, v := range res {
        if v != nil {
            val[i] = v.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable)
        }
    }
    return val, nil
}
// ToGetRequestInformation returns a list of all branches in the artifact. Each branch is a list of version identifiers,ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemBranchesRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemBranchesRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    return requestInfo, nil
}
// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *ItemArtifactsItemBranchesRequestBuilder) WithUrl(rawUrl string)(*ItemArtifactsItemBranchesRequestBuilder) {
    return NewItemArtifactsItemBranchesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
