package groups

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder get a graph representation of artifact references.
type ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemVersionsItemReferencesGraphRequestBuilderGetQueryParameters retrieves a graph representation of all references for a single version of an artifact. The graph includes nodes representing artifacts, edges representing references between them, and metadata about the graph structure including cycle detection.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
type ItemArtifactsItemVersionsItemReferencesGraphRequestBuilderGetQueryParameters struct {
	// The maximum depth of the reference graph to traverse. Can be 1, 2, 3, or 0 for unlimited. Defaults to 3.
	Depth *int32 `uriparametername:"depth"`
	// The direction of references to include in the graph. Can be OUTBOUND (artifacts this version references), INBOUND (artifacts that reference this version), or BOTH. Defaults to OUTBOUND.
	// Deprecated: This property is deprecated, use DirectionAsReferenceGraphDirection instead
	Direction *string `uriparametername:"direction"`
	// The direction of references to include in the graph. Can be OUTBOUND (artifacts this version references), INBOUND (artifacts that reference this version), or BOTH. Defaults to OUTBOUND.
	DirectionAsReferenceGraphDirection *iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ReferenceGraphDirection `uriparametername:"direction"`
}

// ItemArtifactsItemVersionsItemReferencesGraphRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsItemReferencesGraphRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilderGetQueryParameters
}

// NewItemArtifactsItemVersionsItemReferencesGraphRequestBuilderInternal instantiates a new ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemReferencesGraphRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder {
	m := &ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/references/graph{?depth*,direction*}", pathParameters),
	}
	return m
}

// NewItemArtifactsItemVersionsItemReferencesGraphRequestBuilder instantiates a new ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemReferencesGraphRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemVersionsItemReferencesGraphRequestBuilderInternal(urlParams, requestAdapter)
}

// Get retrieves a graph representation of all references for a single version of an artifact. The graph includes nodes representing artifacts, edges representing references between them, and metadata about the graph structure including cycle detection.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a ReferenceGraphable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilderGetRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ReferenceGraphable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateReferenceGraphFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ReferenceGraphable), nil
}

// ToGetRequestInformation retrieves a graph representation of all references for a single version of an artifact. The graph includes nodes representing artifacts, edges representing references between them, and metadata about the graph structure including cycle detection.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder when successful
func (m *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemVersionsItemReferencesGraphRequestBuilder {
	return NewItemArtifactsItemVersionsItemReferencesGraphRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
