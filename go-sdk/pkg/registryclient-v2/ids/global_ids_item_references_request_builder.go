package ids

import (
	"context"
	idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// GlobalIdsItemReferencesRequestBuilder builds and executes requests for operations under \ids\globalIds\{globalId}\references
type GlobalIdsItemReferencesRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// GlobalIdsItemReferencesRequestBuilderGetQueryParameters returns a list containing all the artifact references using the artifact global ID.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
type GlobalIdsItemReferencesRequestBuilderGetQueryParameters struct {
	// Determines the type of reference to return, either INBOUND or OUTBOUND.  Defaults to OUTBOUND.
	// Deprecated: This property is deprecated, use RefTypeAsReferenceType instead
	RefType *string `uriparametername:"refType"`
	// Determines the type of reference to return, either INBOUND or OUTBOUND.  Defaults to OUTBOUND.
	RefTypeAsReferenceType *idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ReferenceType `uriparametername:"refType"`
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

// NewGlobalIdsItemReferencesRequestBuilderInternal instantiates a new GlobalIdsItemReferencesRequestBuilder and sets the default values.
func NewGlobalIdsItemReferencesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GlobalIdsItemReferencesRequestBuilder {
	m := &GlobalIdsItemReferencesRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/globalIds/{globalId}/references{?refType*}", pathParameters),
	}
	return m
}

// NewGlobalIdsItemReferencesRequestBuilder instantiates a new GlobalIdsItemReferencesRequestBuilder and sets the default values.
func NewGlobalIdsItemReferencesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GlobalIdsItemReferencesRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewGlobalIdsItemReferencesRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a list containing all the artifact references using the artifact global ID.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
// returns a []ArtifactReferenceable when successful
func (m *GlobalIdsItemReferencesRequestBuilder) Get(ctx context.Context, requestConfiguration *GlobalIdsItemReferencesRequestBuilderGetRequestConfiguration) ([]idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ArtifactReferenceable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateArtifactReferenceFromDiscriminatorValue, nil)
	if err != nil {
		return nil, err
	}
	val := make([]idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ArtifactReferenceable, len(res))
	for i, v := range res {
		if v != nil {
			val[i] = v.(idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ArtifactReferenceable)
		}
	}
	return val, nil
}

// ToGetRequestInformation returns a list containing all the artifact references using the artifact global ID.This operation may fail for one of the following reasons:* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
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
// returns a *GlobalIdsItemReferencesRequestBuilder when successful
func (m *GlobalIdsItemReferencesRequestBuilder) WithUrl(rawUrl string) *GlobalIdsItemReferencesRequestBuilder {
	return NewGlobalIdsItemReferencesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
