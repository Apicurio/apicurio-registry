package search

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// VersionsRequestBuilder search for versions in the registry.
type VersionsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// VersionsRequestBuilderGetQueryParameters returns a paginated list of all versions that match the provided filter criteria.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
type VersionsRequestBuilderGetQueryParameters struct {
	// Filter by artifactId.
	ArtifactId *string `uriparametername:"artifactId"`
	// Filter by artifact type (`AVRO`, `JSON`, etc).
	ArtifactType *string `uriparametername:"artifactType"`
	// Filter by contentId.
	ContentId *int64 `uriparametername:"contentId"`
	// Filter by description.
	Description *string `uriparametername:"description"`
	// Filter by globalId.
	GlobalId *int64 `uriparametername:"globalId"`
	// Filter by artifact group.
	GroupId *string `uriparametername:"groupId"`
	// Filter by one or more name/value label.  Separate each name/value pair using a colon.  Forexample `labels=foo:bar` will return only artifacts with a label named `foo`and value `bar`.
	Labels []string `uriparametername:"labels"`
	// The number of versions to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// Filter by name.
	Name *string `uriparametername:"name"`
	// The number of versions to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	// Deprecated: This property is deprecated, use OrderAsSortOrder instead
	Order *string `uriparametername:"order"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	OrderAsSortOrder *i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.SortOrder `uriparametername:"order"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	// Deprecated: This property is deprecated, use OrderbyAsVersionSortBy instead
	Orderby *string `uriparametername:"orderby"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	OrderbyAsVersionSortBy *i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.VersionSortBy `uriparametername:"orderby"`
	// Filter by version state.
	// Deprecated: This property is deprecated, use StateAsVersionState instead
	State *string `uriparametername:"state"`
	// Filter by version state.
	StateAsVersionState *i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.VersionState `uriparametername:"state"`
	// Filter by version number.
	Version *string `uriparametername:"version"`
}

// VersionsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type VersionsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *VersionsRequestBuilderGetQueryParameters
}

// VersionsRequestBuilderPostQueryParameters returns a paginated list of all versions that match the posted content.This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* A server error occurred (HTTP error `500`)
type VersionsRequestBuilderPostQueryParameters struct {
	// Filter by artifact Id.
	ArtifactId *string `uriparametername:"artifactId"`
	// Indicates the type of artifact represented by the content being used for the search.  This is only needed when using the `canonical` query parameter, so that the server knows how to canonicalize the content prior to searching for matching versions.
	ArtifactType *string `uriparametername:"artifactType"`
	// Parameter that can be set to `true` to indicate that the server should "canonicalize" the content when searching for matching artifacts.  Canonicalization is unique to each artifact type, but typically involves removing any extra whitespace and formatting the content in a consistent manner.  Must be used along with the `artifactType` query parameter.
	Canonical *bool `uriparametername:"canonical"`
	// Filter by group Id.
	GroupId *string `uriparametername:"groupId"`
	// The number of versions to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of versions to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	// Deprecated: This property is deprecated, use OrderAsSortOrder instead
	Order *string `uriparametername:"order"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	OrderAsSortOrder *i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.SortOrder `uriparametername:"order"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	// Deprecated: This property is deprecated, use OrderbyAsVersionSortBy instead
	Orderby *string `uriparametername:"orderby"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	OrderbyAsVersionSortBy *i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.VersionSortBy `uriparametername:"orderby"`
}

// VersionsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type VersionsRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *VersionsRequestBuilderPostQueryParameters
}

// NewVersionsRequestBuilderInternal instantiates a new VersionsRequestBuilder and sets the default values.
func NewVersionsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *VersionsRequestBuilder {
	m := &VersionsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/search/versions{?artifactId*,artifactType*,canonical*,contentId*,description*,globalId*,groupId*,labels*,limit*,name*,offset*,order*,orderby*,state*,version*}", pathParameters),
	}
	return m
}

// NewVersionsRequestBuilder instantiates a new VersionsRequestBuilder and sets the default values.
func NewVersionsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *VersionsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewVersionsRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a paginated list of all versions that match the provided filter criteria.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a VersionSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 500 status code
func (m *VersionsRequestBuilder) Get(ctx context.Context, requestConfiguration *VersionsRequestBuilderGetRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.VersionSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateVersionSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.VersionSearchResultsable), nil
}

// Post returns a paginated list of all versions that match the posted content.This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* A server error occurred (HTTP error `500`)
// returns a VersionSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *VersionsRequestBuilder) Post(ctx context.Context, body []byte, contentType *string, requestConfiguration *VersionsRequestBuilderPostRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.VersionSearchResultsable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, contentType, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateVersionSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.VersionSearchResultsable), nil
}

// ToGetRequestInformation returns a paginated list of all versions that match the provided filter criteria.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *VersionsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *VersionsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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

// ToPostRequestInformation returns a paginated list of all versions that match the posted content.This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *VersionsRequestBuilder) ToPostRequestInformation(ctx context.Context, body []byte, contentType *string, requestConfiguration *VersionsRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *VersionsRequestBuilder when successful
func (m *VersionsRequestBuilder) WithUrl(rawUrl string) *VersionsRequestBuilder {
	return NewVersionsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
