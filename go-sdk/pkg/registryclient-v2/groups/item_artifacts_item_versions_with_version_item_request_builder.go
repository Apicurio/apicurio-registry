package groups

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/models"
)

// ItemArtifactsItemVersionsWithVersionItemRequestBuilder manage a single version of a single artifact in the registry.
type ItemArtifactsItemVersionsWithVersionItemRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ItemArtifactsItemVersionsWithVersionItemRequestBuilderDeleteRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsWithVersionItemRequestBuilderDeleteRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// ItemArtifactsItemVersionsWithVersionItemRequestBuilderGetQueryParameters retrieves a single version of the artifact content.  Both the `artifactId` and theunique `version` number must be provided.  The `Content-Type` of the response depends on the artifact type.  In most cases, this is `application/json`, but for some types it may be different (for example, `PROTOBUF`).This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
type ItemArtifactsItemVersionsWithVersionItemRequestBuilderGetQueryParameters struct {
    // Allows the user to specify how references in the content should be treated.
    // Deprecated: This property is deprecated, use referencesAsHandleReferencesType instead
    References *string `uriparametername:"references"`
    // Allows the user to specify how references in the content should be treated.
    ReferencesAsHandleReferencesType *i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.HandleReferencesType `uriparametername:"references"`
}
// ItemArtifactsItemVersionsWithVersionItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsWithVersionItemRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
    // Request query parameters
    QueryParameters *ItemArtifactsItemVersionsWithVersionItemRequestBuilderGetQueryParameters
}
// Comments manage a collection of comments for an artifact version
func (m *ItemArtifactsItemVersionsWithVersionItemRequestBuilder) Comments()(*ItemArtifactsItemVersionsItemCommentsRequestBuilder) {
    return NewItemArtifactsItemVersionsItemCommentsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// NewItemArtifactsItemVersionsWithVersionItemRequestBuilderInternal instantiates a new WithVersionItemRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsWithVersionItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemVersionsWithVersionItemRequestBuilder) {
    m := &ItemArtifactsItemVersionsWithVersionItemRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/versions/{version}{?references*}", pathParameters),
    }
    return m
}
// NewItemArtifactsItemVersionsWithVersionItemRequestBuilder instantiates a new WithVersionItemRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsWithVersionItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemArtifactsItemVersionsWithVersionItemRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemArtifactsItemVersionsWithVersionItemRequestBuilderInternal(urlParams, requestAdapter)
}
// Delete deletes a single version of the artifact. Parameters `groupId`, `artifactId` and the unique `version`are needed. If this is the only version of the artifact, this operation is the same as deleting the entire artifact.This feature is disabled by default and it's discouraged for normal usage. To enable it, set the `apicurio.rest.artifact.deletion.enabled` property to true. This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`) * Feature is disabled (HTTP error `405`) * A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsWithVersionItemRequestBuilder) Delete(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsWithVersionItemRequestBuilderDeleteRequestConfiguration)(error) {
    requestInfo, err := m.ToDeleteRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
        "405": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
        "500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
    }
    err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
    if err != nil {
        return err
    }
    return nil
}
// Get retrieves a single version of the artifact content.  Both the `artifactId` and theunique `version` number must be provided.  The `Content-Type` of the response depends on the artifact type.  In most cases, this is `application/json`, but for some types it may be different (for example, `PROTOBUF`).This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsWithVersionItemRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsWithVersionItemRequestBuilderGetRequestConfiguration)([]byte, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "404": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
        "500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.SendPrimitive(ctx, requestInfo, "[]byte", errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.([]byte), nil
}
// Meta manage the metadata for a single version of an artifact in the registry.
func (m *ItemArtifactsItemVersionsWithVersionItemRequestBuilder) Meta()(*ItemArtifactsItemVersionsItemMetaRequestBuilder) {
    return NewItemArtifactsItemVersionsItemMetaRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// References manage the references for a single version of an artifact in the registry.
func (m *ItemArtifactsItemVersionsWithVersionItemRequestBuilder) References()(*ItemArtifactsItemVersionsItemReferencesRequestBuilder) {
    return NewItemArtifactsItemVersionsItemReferencesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// State manage the state of a specific artifact version.
func (m *ItemArtifactsItemVersionsWithVersionItemRequestBuilder) State()(*ItemArtifactsItemVersionsItemStateRequestBuilder) {
    return NewItemArtifactsItemVersionsItemStateRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
// ToDeleteRequestInformation deletes a single version of the artifact. Parameters `groupId`, `artifactId` and the unique `version`are needed. If this is the only version of the artifact, this operation is the same as deleting the entire artifact.This feature is disabled by default and it's discouraged for normal usage. To enable it, set the `apicurio.rest.artifact.deletion.enabled` property to true. This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`) * Feature is disabled (HTTP error `405`) * A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsWithVersionItemRequestBuilder) ToDeleteRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsWithVersionItemRequestBuilderDeleteRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.DELETE, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    return requestInfo, nil
}
// ToGetRequestInformation retrieves a single version of the artifact content.  Both the `artifactId` and theunique `version` number must be provided.  The `Content-Type` of the response depends on the artifact type.  In most cases, this is `application/json`, but for some types it may be different (for example, `PROTOBUF`).This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsWithVersionItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsWithVersionItemRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        if requestConfiguration.QueryParameters != nil {
            requestInfo.AddQueryParameters(*(requestConfiguration.QueryParameters))
        }
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "*/*, application/json")
    return requestInfo, nil
}
// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *ItemArtifactsItemVersionsWithVersionItemRequestBuilder) WithUrl(rawUrl string)(*ItemArtifactsItemVersionsWithVersionItemRequestBuilder) {
    return NewItemArtifactsItemVersionsWithVersionItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
