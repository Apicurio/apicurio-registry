package groups

import (
	"context"
	idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemVersionsRequestBuilder manage all the versions of an artifact in the registry.
type ItemArtifactsItemVersionsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemVersionsRequestBuilderGetQueryParameters returns a list of all versions of the artifact.  The result set is paged.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
type ItemArtifactsItemVersionsRequestBuilderGetQueryParameters struct {
	// The number of versions to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of versions to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
}

// ItemArtifactsItemVersionsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ItemArtifactsItemVersionsRequestBuilderGetQueryParameters
}

// ItemArtifactsItemVersionsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ByVersion manage a single version of a single artifact in the registry.
// returns a *ItemArtifactsItemVersionsWithVersionItemRequestBuilder when successful
func (m *ItemArtifactsItemVersionsRequestBuilder) ByVersion(version string) *ItemArtifactsItemVersionsWithVersionItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if version != "" {
		urlTplParams["version"] = version
	}
	return NewItemArtifactsItemVersionsWithVersionItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewItemArtifactsItemVersionsRequestBuilderInternal instantiates a new ItemArtifactsItemVersionsRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsRequestBuilder {
	m := &ItemArtifactsItemVersionsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/versions{?limit*,offset*}", pathParameters),
	}
	return m
}

// NewItemArtifactsItemVersionsRequestBuilder instantiates a new ItemArtifactsItemVersionsRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemVersionsRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a list of all versions of the artifact.  The result set is paged.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a VersionSearchResultsable when successful
// returns a Error error when the service returns a 404 status code
// returns a Error error when the service returns a 500 status code
func (m *ItemArtifactsItemVersionsRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsRequestBuilderGetRequestConfiguration) (idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.VersionSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
		"500": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateVersionSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.VersionSearchResultsable), nil
}

// Post creates a new version of the artifact by uploading new content.  The configured rules forthe artifact are applied, and if they all pass, the new content is added as the most recent version of the artifact.  If any of the rules fail, an error is returned.The body of the request can be the raw content of the new artifact version, or the raw content and a set of references pointing to other artifacts, and the typeof that content should match the artifact's type (for example if the artifact type is `AVRO`then the content of the request should be an Apache Avro document).This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* No artifact with this `artifactId` exists (HTTP error `404`)* The new content violates one of the rules configured for the artifact (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a VersionMetaDataable when successful
// returns a Error error when the service returns a 404 status code
// returns a RuleViolationError error when the service returns a 409 status code
// returns a Error error when the service returns a 500 status code
func (m *ItemArtifactsItemVersionsRequestBuilder) Post(ctx context.Context, body idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ArtifactContentable, requestConfiguration *ItemArtifactsItemVersionsRequestBuilderPostRequestConfiguration) (idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.VersionMetaDataable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
		"409": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateRuleViolationErrorFromDiscriminatorValue,
		"500": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateVersionMetaDataFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.VersionMetaDataable), nil
}

// ToGetRequestInformation returns a list of all versions of the artifact.  The result set is paged.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemVersionsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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

// ToPostRequestInformation creates a new version of the artifact by uploading new content.  The configured rules forthe artifact are applied, and if they all pass, the new content is added as the most recent version of the artifact.  If any of the rules fail, an error is returned.The body of the request can be the raw content of the new artifact version, or the raw content and a set of references pointing to other artifacts, and the typeof that content should match the artifact's type (for example if the artifact type is `AVRO`then the content of the request should be an Apache Avro document).This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* No artifact with this `artifactId` exists (HTTP error `404`)* The new content violates one of the rules configured for the artifact (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemVersionsRequestBuilder) ToPostRequestInformation(ctx context.Context, body idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ArtifactContentable, requestConfiguration *ItemArtifactsItemVersionsRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	err := requestInfo.SetContentFromParsable(ctx, m.BaseRequestBuilder.RequestAdapter, "application/vnd.create.extended+json", body)
	if err != nil {
		return nil, err
	}
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ItemArtifactsItemVersionsRequestBuilder when successful
func (m *ItemArtifactsItemVersionsRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemVersionsRequestBuilder {
	return NewItemArtifactsItemVersionsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
