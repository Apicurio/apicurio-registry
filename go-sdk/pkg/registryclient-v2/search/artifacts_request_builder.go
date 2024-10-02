package search

import (
	"context"
	i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/models"
	ie8be0c90121d7d083037815d4b53c9b92094bdd04db14cfd7f4e165f59ceda76 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/search/artifacts"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ArtifactsRequestBuilder search for artifacts in the registry.
type ArtifactsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ArtifactsRequestBuilderGetQueryParameters returns a paginated list of all artifacts that match the provided filter criteria.
type ArtifactsRequestBuilderGetQueryParameters struct {
	// Filter by contentId.
	ContentId *int64 `uriparametername:"contentId"`
	// Filter by description.
	Description *string `uriparametername:"description"`
	// Filter by globalId.
	GlobalId *int64 `uriparametername:"globalId"`
	// Filter by artifact group.
	Group *string `uriparametername:"group"`
	// Filter by label.  Include one or more label to only return artifacts containing all of thespecified labels.
	Labels []string `uriparametername:"labels"`
	// The number of artifacts to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// Filter by artifact name.
	Name *string `uriparametername:"name"`
	// The number of artifacts to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	// Deprecated: This property is deprecated, use OrderAsSortOrder instead
	Order *string `uriparametername:"order"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	OrderAsSortOrder *i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.SortOrder `uriparametername:"order"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	// Deprecated: This property is deprecated, use OrderbyAsSortBy instead
	Orderby *string `uriparametername:"orderby"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	OrderbyAsSortBy *i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.SortBy `uriparametername:"orderby"`
	// Filter by one or more name/value property.  Separate each name/value pair using a colon.  Forexample `properties=foo:bar` will return only artifacts with a custom property named `foo`and value `bar`.
	Properties []string `uriparametername:"properties"`
}

// ArtifactsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ArtifactsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ArtifactsRequestBuilderGetQueryParameters
}

// ArtifactsRequestBuilderPostQueryParameters returns a paginated list of all artifacts with at least one version that matches theposted content.
type ArtifactsRequestBuilderPostQueryParameters struct {
	// Indicates the type of artifact represented by the content being used for the search.  This is only needed when using the `canonical` query parameter, so that the server knows how to canonicalize the content prior to searching for matching artifacts.
	ArtifactType *string `uriparametername:"artifactType"`
	// Parameter that can be set to `true` to indicate that the server should "canonicalize" the content when searching for matching artifacts.  Canonicalization is unique to each artifact type, but typically involves removing any extra whitespace and formatting the content in a consistent manner.  Must be used along with the `artifactType` query parameter.
	Canonical *bool `uriparametername:"canonical"`
	// The number of artifacts to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of artifacts to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	// Deprecated: This property is deprecated, use OrderAsPostOrderQueryParameterType instead
	Order *string `uriparametername:"order"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	OrderAsPostOrderQueryParameterType *ie8be0c90121d7d083037815d4b53c9b92094bdd04db14cfd7f4e165f59ceda76.PostOrderQueryParameterType `uriparametername:"order"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	// Deprecated: This property is deprecated, use OrderbyAsPostOrderbyQueryParameterType instead
	Orderby *string `uriparametername:"orderby"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	OrderbyAsPostOrderbyQueryParameterType *ie8be0c90121d7d083037815d4b53c9b92094bdd04db14cfd7f4e165f59ceda76.PostOrderbyQueryParameterType `uriparametername:"orderby"`
}

// ArtifactsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ArtifactsRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ArtifactsRequestBuilderPostQueryParameters
}

// NewArtifactsRequestBuilderInternal instantiates a new ArtifactsRequestBuilder and sets the default values.
func NewArtifactsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ArtifactsRequestBuilder {
	m := &ArtifactsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/search/artifacts{?artifactType*,canonical*,contentId*,description*,globalId*,group*,labels*,limit*,name*,offset*,order*,orderby*,properties*}", pathParameters),
	}
	return m
}

// NewArtifactsRequestBuilder instantiates a new ArtifactsRequestBuilder and sets the default values.
func NewArtifactsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ArtifactsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewArtifactsRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a paginated list of all artifacts that match the provided filter criteria.
// returns a ArtifactSearchResultsable when successful
// returns a Error error when the service returns a 500 status code
func (m *ArtifactsRequestBuilder) Get(ctx context.Context, requestConfiguration *ArtifactsRequestBuilderGetRequestConfiguration) (i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ArtifactSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateArtifactSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ArtifactSearchResultsable), nil
}

// Post returns a paginated list of all artifacts with at least one version that matches theposted content.
// returns a ArtifactSearchResultsable when successful
// returns a Error error when the service returns a 500 status code
func (m *ArtifactsRequestBuilder) Post(ctx context.Context, body []byte, contentType *string, requestConfiguration *ArtifactsRequestBuilderPostRequestConfiguration) (i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ArtifactSearchResultsable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, contentType, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateArtifactSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.ArtifactSearchResultsable), nil
}

// ToGetRequestInformation returns a paginated list of all artifacts that match the provided filter criteria.
// returns a *RequestInformation when successful
func (m *ArtifactsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ArtifactsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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

// ToPostRequestInformation returns a paginated list of all artifacts with at least one version that matches theposted content.
// returns a *RequestInformation when successful
func (m *ArtifactsRequestBuilder) ToPostRequestInformation(ctx context.Context, body []byte, contentType *string, requestConfiguration *ArtifactsRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		if requestConfiguration.QueryParameters != nil {
			requestInfo.AddQueryParameters(*(requestConfiguration.QueryParameters))
		}
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	requestInfo.SetStreamContentAndContentType(body, *contentType)
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ArtifactsRequestBuilder when successful
func (m *ArtifactsRequestBuilder) WithUrl(rawUrl string) *ArtifactsRequestBuilder {
	return NewArtifactsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
