package content

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ReferencesRequestBuilder detect external references in artifact content.
type ReferencesRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ReferencesRequestBuilderPostQueryParameters analyzes the provided content and returns a list of external references found within it.  The artifact type is used to determine the appropriate reference detection strategy.  If no artifact type is provided, the system will attempt to auto-detect it from the content.
type ReferencesRequestBuilderPostQueryParameters struct {
	// The type of artifact represented by the content.  If not provided, the server will attempt to auto-detect the type from the content.
	ArtifactType *string `uriparametername:"artifactType"`
}

// ReferencesRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ReferencesRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ReferencesRequestBuilderPostQueryParameters
}

// NewReferencesRequestBuilderInternal instantiates a new ReferencesRequestBuilder and sets the default values.
func NewReferencesRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ReferencesRequestBuilder {
	m := &ReferencesRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/content/references{?artifactType*}", pathParameters),
	}
	return m
}

// NewReferencesRequestBuilder instantiates a new ReferencesRequestBuilder and sets the default values.
func NewReferencesRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ReferencesRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewReferencesRequestBuilderInternal(urlParams, requestAdapter)
}

// Post analyzes the provided content and returns a list of external references found within it.  The artifact type is used to determine the appropriate reference detection strategy.  If no artifact type is provided, the system will attempt to auto-detect it from the content.
// returns a []ArtifactReferenceable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ReferencesRequestBuilder) Post(ctx context.Context, body iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.VersionContentable, requestConfiguration *ReferencesRequestBuilderPostRequestConfiguration) ([]iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactReferenceable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateArtifactReferenceFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	val := make([]iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactReferenceable, len(res))
	for i, v := range res {
		if v != nil {
			val[i] = v.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactReferenceable)
		}
	}
	return val, nil
}

// ToPostRequestInformation analyzes the provided content and returns a list of external references found within it.  The artifact type is used to determine the appropriate reference detection strategy.  If no artifact type is provided, the system will attempt to auto-detect it from the content.
// returns a *RequestInformation when successful
func (m *ReferencesRequestBuilder) ToPostRequestInformation(ctx context.Context, body iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.VersionContentable, requestConfiguration *ReferencesRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		if requestConfiguration.QueryParameters != nil {
			requestInfo.AddQueryParameters(*(requestConfiguration.QueryParameters))
		}
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	err := requestInfo.SetContentFromParsable(ctx, m.BaseRequestBuilder.RequestAdapter, "application/json", body)
	if err != nil {
		return nil, err
	}
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ReferencesRequestBuilder when successful
func (m *ReferencesRequestBuilder) WithUrl(rawUrl string) *ReferencesRequestBuilder {
	return NewReferencesRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
