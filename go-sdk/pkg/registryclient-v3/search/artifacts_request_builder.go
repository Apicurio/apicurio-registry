package search

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ArtifactsRequestBuilder search for artifacts in the registry.
type ArtifactsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ArtifactsRequestBuilderGetQueryParameters returns a paginated list of all artifacts that match the provided filter criteria.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
type ArtifactsRequestBuilderGetQueryParameters struct {
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
	OrderAsSortOrder *iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.SortOrder `uriparametername:"order"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	// Deprecated: This property is deprecated, use OrderbyAsArtifactSortBy instead
	Orderby *string `uriparametername:"orderby"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	OrderbyAsArtifactSortBy *iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSortBy `uriparametername:"orderby"`
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

// ArtifactsRequestBuilderPostQueryParameters returns a paginated list of all artifacts with at least one version that matches theposted content.This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* A server error occurred (HTTP error `500`)
type ArtifactsRequestBuilderPostQueryParameters struct {
	// Indicates the type of artifact represented by the content being used for the search.  This is only needed when using the `canonical` query parameter, so that the server knows how to canonicalize the content prior to searching for matching artifacts.
	ArtifactType *string `uriparametername:"artifactType"`
	// Parameter that can be set to `true` to indicate that the server should "canonicalize" the content when searching for matching artifacts.  Canonicalization is unique to each artifact type, but typically involves removing any extra whitespace and formatting the content in a consistent manner.  Must be used along with the `artifactType` query parameter.
	Canonical *bool `uriparametername:"canonical"`
	// Filter by artifact group.
	GroupId *string `uriparametername:"groupId"`
	// The number of artifacts to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of artifacts to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	// Deprecated: This property is deprecated, use OrderAsSortOrder instead
	Order *string `uriparametername:"order"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	OrderAsSortOrder *iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.SortOrder `uriparametername:"order"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	// Deprecated: This property is deprecated, use OrderbyAsArtifactSortBy instead
	Orderby *string `uriparametername:"orderby"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	OrderbyAsArtifactSortBy *iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSortBy `uriparametername:"orderby"`
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
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/search/artifacts{?artifactId*,artifactType*,canonical*,contentId*,description*,globalId*,groupId*,labels*,limit*,name*,offset*,order*,orderby*}", pathParameters),
	}
	return m
}

// NewArtifactsRequestBuilder instantiates a new ArtifactsRequestBuilder and sets the default values.
func NewArtifactsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ArtifactsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewArtifactsRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a paginated list of all artifacts that match the provided filter criteria.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a ArtifactSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ArtifactsRequestBuilder) Get(ctx context.Context, requestConfiguration *ArtifactsRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateArtifactSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSearchResultsable), nil
}

// Post returns a paginated list of all artifacts with at least one version that matches theposted content.This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* A server error occurred (HTTP error `500`)
// returns a ArtifactSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ArtifactsRequestBuilder) Post(ctx context.Context, body []byte, contentType *string, requestConfiguration *ArtifactsRequestBuilderPostRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSearchResultsable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, contentType, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateArtifactSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSearchResultsable), nil
}

// ToGetRequestInformation returns a paginated list of all artifacts that match the provided filter criteria.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
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

// ToPostRequestInformation returns a paginated list of all artifacts with at least one version that matches theposted content.This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* A server error occurred (HTTP error `500`)
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
