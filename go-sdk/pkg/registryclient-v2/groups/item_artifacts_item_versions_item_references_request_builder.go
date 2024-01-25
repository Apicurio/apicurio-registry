package groups

import (
	"context"
	i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemVersionsItemReferencesRequestBuilder manage the references for a single version of an artifact in the registry.
type ItemArtifactsItemVersionsItemReferencesRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemVersionsItemReferencesRequestBuilderGetQueryParameters retrieves all references for a single version of an artifact.  Both the `artifactId` and theunique `version` number must be provided.  Using the `refType` query parameter, it is possibleto retrieve an array of either the inbound or outbound references.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
type ItemArtifactsItemVersionsItemReferencesRequestBuilderGetQueryParameters struct {
	// Determines the type of reference to return, either INBOUND or OUTBOUND.  Defaults to OUTBOUND.
	// Deprecated: This property is deprecated, use refTypeAsReferenceType instead
	RefType *string `uriparametername:"refType"`
	// Determines the type of reference to return, either INBOUND or OUTBOUND.  Defaults to OUTBOUND.
	RefTypeAsReferenceType *i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ReferenceType `uriparametername:"refType"`
}

// ItemArtifactsItemVersionsItemReferencesRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsItemReferencesRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ItemArtifactsItemVersionsItemReferencesRequestBuilderGetQueryParameters
}

// NewItemArtifactsItemVersionsItemReferencesRequestBuilderInternal instantiates a new ReferencesRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemReferencesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsItemReferencesRequestBuilder {
	m := &ItemArtifactsItemVersionsItemReferencesRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/versions/{version}/references{?refType*}", pathParameters),
	}
	return m
}

// NewItemArtifactsItemVersionsItemReferencesRequestBuilder instantiates a new ReferencesRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemReferencesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsItemReferencesRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemVersionsItemReferencesRequestBuilderInternal(urlParams, requestAdapter)
}

// Get retrieves all references for a single version of an artifact.  Both the `artifactId` and theunique `version` number must be provided.  Using the `refType` query parameter, it is possibleto retrieve an array of either the inbound or outbound references.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsItemReferencesRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsItemReferencesRequestBuilderGetRequestConfiguration) ([]i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ArtifactReferenceable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
		"500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateArtifactReferenceFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	val := make([]i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ArtifactReferenceable, len(res))
	for i, v := range res {
		if v != nil {
			val[i] = v.(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ArtifactReferenceable)
		}
	}
	return val, nil
}

// ToGetRequestInformation retrieves all references for a single version of an artifact.  Both the `artifactId` and theunique `version` number must be provided.  Using the `refType` query parameter, it is possibleto retrieve an array of either the inbound or outbound references.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemVersionsItemReferencesRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsItemReferencesRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *ItemArtifactsItemVersionsItemReferencesRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemVersionsItemReferencesRequestBuilder {
	return NewItemArtifactsItemVersionsItemReferencesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
