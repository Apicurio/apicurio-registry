package ids

import (
	"context"
	i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// GlobalIdsItemReferencesRequestBuilder builds and executes requests for operations under \ids\globalIds\{globalId}\references
type GlobalIdsItemReferencesRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// GlobalIdsItemReferencesRequestBuilderGetQueryParameters returns a list containing all the artifact references using the artifact global ID.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
type GlobalIdsItemReferencesRequestBuilderGetQueryParameters struct {
	// Determines the type of reference to return, either INBOUND or OUTBOUND.  Defaults to OUTBOUND.
	// Deprecated: This property is deprecated, use refTypeAsReferenceType instead
	RefType *string `uriparametername:"refType"`
	// Determines the type of reference to return, either INBOUND or OUTBOUND.  Defaults to OUTBOUND.
	RefTypeAsReferenceType *i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ReferenceType `uriparametername:"refType"`
}

// GlobalIdsItemReferencesRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type GlobalIdsItemReferencesRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *GlobalIdsItemReferencesRequestBuilderGetQueryParameters
}

// NewGlobalIdsItemReferencesRequestBuilderInternal instantiates a new ReferencesRequestBuilder and sets the default values.
func NewGlobalIdsItemReferencesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GlobalIdsItemReferencesRequestBuilder {
	m := &GlobalIdsItemReferencesRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/globalIds/{globalId}/references{?refType*}", pathParameters),
	}
	return m
}

// NewGlobalIdsItemReferencesRequestBuilder instantiates a new ReferencesRequestBuilder and sets the default values.
func NewGlobalIdsItemReferencesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GlobalIdsItemReferencesRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewGlobalIdsItemReferencesRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a list containing all the artifact references using the artifact global ID.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
func (m *GlobalIdsItemReferencesRequestBuilder) Get(ctx context.Context, requestConfiguration *GlobalIdsItemReferencesRequestBuilderGetRequestConfiguration) ([]i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ArtifactReferenceable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateArtifactReferenceFromDiscriminatorValue, nil)
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

// ToGetRequestInformation returns a list containing all the artifact references using the artifact global ID.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
func (m *GlobalIdsItemReferencesRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *GlobalIdsItemReferencesRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
func (m *GlobalIdsItemReferencesRequestBuilder) WithUrl(rawUrl string) *GlobalIdsItemReferencesRequestBuilder {
	return NewGlobalIdsItemReferencesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
