package ids

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ContentIdsWithContentItemRequestBuilder access artifact content utilizing the unique content identifier for that content.
type ContentIdsWithContentItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ContentIdsWithContentItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ContentIdsWithContentItemRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewContentIdsWithContentItemRequestBuilderInternal instantiates a new ContentIdsWithContentItemRequestBuilder and sets the default values.
func NewContentIdsWithContentItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentIdsWithContentItemRequestBuilder {
	m := &ContentIdsWithContentItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/contentIds/{contentId}", pathParameters),
	}
	return m
}

// NewContentIdsWithContentItemRequestBuilder instantiates a new ContentIdsWithContentItemRequestBuilder and sets the default values.
func NewContentIdsWithContentItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentIdsWithContentItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContentIdsWithContentItemRequestBuilderInternal(urlParams, requestAdapter)
}

// Get gets the content for an artifact version in the registry using the unique contentidentifier for that content.  This content ID may be shared by multiple artifactversions in the case where the artifact versions are identical.This operation may fail for one of the following reasons:* No content with this `contentId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a []byte when successful
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ContentIdsWithContentItemRequestBuilder) Get(ctx context.Context, requestConfiguration *ContentIdsWithContentItemRequestBuilderGetRequestConfiguration) ([]byte, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
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

// References the references property
// returns a *ContentIdsItemReferencesRequestBuilder when successful
func (m *ContentIdsWithContentItemRequestBuilder) References() *ContentIdsItemReferencesRequestBuilder {
	return NewContentIdsItemReferencesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// ToGetRequestInformation gets the content for an artifact version in the registry using the unique contentidentifier for that content.  This content ID may be shared by multiple artifactversions in the case where the artifact versions are identical.This operation may fail for one of the following reasons:* No content with this `contentId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ContentIdsWithContentItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ContentIdsWithContentItemRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "*/*, application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ContentIdsWithContentItemRequestBuilder when successful
func (m *ContentIdsWithContentItemRequestBuilder) WithUrl(rawUrl string) *ContentIdsWithContentItemRequestBuilder {
	return NewContentIdsWithContentItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
