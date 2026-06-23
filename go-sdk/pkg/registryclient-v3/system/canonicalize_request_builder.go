package system

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// CanonicalizeRequestBuilder canonicalize content
type CanonicalizeRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// CanonicalizeRequestBuilderPostQueryParameters canonicalizes the provided content using the rules for the specified artifact type.  Canonicalization is specific to each artifact type, but typically involves removing extra whitespace, sorting object keys, and formatting the content in a consistent manner.  The canonicalized content is returned as the response body.This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* The artifact type was not provided or was invalid (HTTP error `400`)* A server error occurred (HTTP error `500`)
type CanonicalizeRequestBuilderPostQueryParameters struct {
	// Indicates the type of artifact represented by the content.  The server uses this to determine how to canonicalize the content.
	ArtifactType *string `uriparametername:"artifactType"`
}

// CanonicalizeRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type CanonicalizeRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *CanonicalizeRequestBuilderPostQueryParameters
}

// NewCanonicalizeRequestBuilderInternal instantiates a new CanonicalizeRequestBuilder and sets the default values.
func NewCanonicalizeRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *CanonicalizeRequestBuilder {
	m := &CanonicalizeRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/system/canonicalize?artifactType={artifactType}", pathParameters),
	}
	return m
}

// NewCanonicalizeRequestBuilder instantiates a new CanonicalizeRequestBuilder and sets the default values.
func NewCanonicalizeRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *CanonicalizeRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewCanonicalizeRequestBuilderInternal(urlParams, requestAdapter)
}

// Post canonicalizes the provided content using the rules for the specified artifact type.  Canonicalization is specific to each artifact type, but typically involves removing extra whitespace, sorting object keys, and formatting the content in a consistent manner.  The canonicalized content is returned as the response body.This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* The artifact type was not provided or was invalid (HTTP error `400`)* A server error occurred (HTTP error `500`)
// returns a []byte when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *CanonicalizeRequestBuilder) Post(ctx context.Context, body []byte, contentType *string, requestConfiguration *CanonicalizeRequestBuilderPostRequestConfiguration) ([]byte, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, contentType, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
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

// ToPostRequestInformation canonicalizes the provided content using the rules for the specified artifact type.  Canonicalization is specific to each artifact type, but typically involves removing extra whitespace, sorting object keys, and formatting the content in a consistent manner.  The canonicalized content is returned as the response body.This operation can fail for the following reasons:* Provided content (request body) was empty (HTTP error `400`)* The artifact type was not provided or was invalid (HTTP error `400`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *CanonicalizeRequestBuilder) ToPostRequestInformation(ctx context.Context, body []byte, contentType *string, requestConfiguration *CanonicalizeRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		if requestConfiguration.QueryParameters != nil {
			requestInfo.AddQueryParameters(*(requestConfiguration.QueryParameters))
		}
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "*/*, application/json")
	requestInfo.SetStreamContentAndContentType(body, *contentType)
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *CanonicalizeRequestBuilder when successful
func (m *CanonicalizeRequestBuilder) WithUrl(rawUrl string) *CanonicalizeRequestBuilder {
	return NewCanonicalizeRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
