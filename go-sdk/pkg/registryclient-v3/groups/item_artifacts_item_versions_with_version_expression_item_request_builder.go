package groups

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder manage a single version of a single artifact in the registry.
type ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderDeleteRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderDeleteRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderGetQueryParameters retrieves a single version of the artifact content.  Both the `artifactId` and theunique `version` number must be provided.  The `Content-Type` of the response depends on the artifact type.  In most cases, this is `application/json`, but for some types it may be different (for example, `PROTOBUF`).This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
type ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderGetQueryParameters struct {
	// Allows the user to specify how references in the content should be treated.
	// Deprecated: This property is deprecated, use referencesAsHandleReferencesType instead
	References *string `uriparametername:"references"`
	// Allows the user to specify how references in the content should be treated.
	ReferencesAsHandleReferencesType *i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.HandleReferencesType `uriparametername:"references"`
}

// ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderGetQueryParameters
}

// Comments manage a collection of comments for an artifact version
func (m *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder) Comments() *ItemArtifactsItemVersionsItemCommentsRequestBuilder {
	return NewItemArtifactsItemVersionsItemCommentsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// NewItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderInternal instantiates a new WithVersionExpressionItemRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder {
	m := &ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}{?references*}", pathParameters),
	}
	return m
}

// NewItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder instantiates a new WithVersionExpressionItemRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderInternal(urlParams, requestAdapter)
}

// Delete deletes a single version of the artifact. Parameters `groupId`, `artifactId` and the unique `version`are needed. If this is the only version of the artifact, this operation is the same as deleting the entire artifact.This feature is disabled by default and it's discouraged for normal usage. To enable it, set the `registry.rest.artifact.deletion.enabled` property to true. This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`) * Feature is disabled (HTTP error `405`) * A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder) Delete(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderDeleteRequestConfiguration) error {
	requestInfo, err := m.ToDeleteRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"405": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// Get retrieves a single version of the artifact content.  Both the `artifactId` and theunique `version` number must be provided.  The `Content-Type` of the response depends on the artifact type.  In most cases, this is `application/json`, but for some types it may be different (for example, `PROTOBUF`).This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderGetRequestConfiguration) ([]byte, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
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
func (m *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder) Meta() *ItemArtifactsItemVersionsItemMetaRequestBuilder {
	return NewItemArtifactsItemVersionsItemMetaRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// References manage the references for a single version of an artifact in the registry.
func (m *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder) References() *ItemArtifactsItemVersionsItemReferencesRequestBuilder {
	return NewItemArtifactsItemVersionsItemReferencesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// State manage the state of a specific artifact version.
func (m *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder) State() *ItemArtifactsItemVersionsItemStateRequestBuilder {
	return NewItemArtifactsItemVersionsItemStateRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// ToDeleteRequestInformation deletes a single version of the artifact. Parameters `groupId`, `artifactId` and the unique `version`are needed. If this is the only version of the artifact, this operation is the same as deleting the entire artifact.This feature is disabled by default and it's discouraged for normal usage. To enable it, set the `registry.rest.artifact.deletion.enabled` property to true. This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`) * Feature is disabled (HTTP error `405`) * A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder) ToDeleteRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderDeleteRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.DELETE, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToGetRequestInformation retrieves a single version of the artifact content.  Both the `artifactId` and theunique `version` number must be provided.  The `Content-Type` of the response depends on the artifact type.  In most cases, this is `application/json`, but for some types it may be different (for example, `PROTOBUF`).This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
func (m *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder {
	return NewItemArtifactsItemVersionsWithVersionExpressionItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
