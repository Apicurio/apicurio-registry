package content

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ContentRequestBuilder upload raw artifact content for later use.
type ContentRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ContentRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ContentRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewContentRequestBuilderInternal instantiates a new ContentRequestBuilder and sets the default values.
func NewContentRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentRequestBuilder {
	m := &ContentRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/content", pathParameters),
	}
	return m
}

// NewContentRequestBuilder instantiates a new ContentRequestBuilder and sets the default values.
func NewContentRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContentRequestBuilderInternal(urlParams, requestAdapter)
}

// Post uploads raw artifact content to the registry, returning a unique content identifier.  The returned content identifier can then be used when creating artifacts or artifact versions.  This is useful when the content is very large and it is not practical to include the content inline when, e.g. creating an artifact.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a ContentCreateResponseable when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ContentRequestBuilder) Post(ctx context.Context, body []byte, contentType *string, requestConfiguration *ContentRequestBuilderPostRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ContentCreateResponseable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, contentType, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateContentCreateResponseFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ContentCreateResponseable), nil
}

// ToPostRequestInformation uploads raw artifact content to the registry, returning a unique content identifier.  The returned content identifier can then be used when creating artifacts or artifact versions.  This is useful when the content is very large and it is not practical to include the content inline when, e.g. creating an artifact.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ContentRequestBuilder) ToPostRequestInformation(ctx context.Context, body []byte, contentType *string, requestConfiguration *ContentRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	requestInfo.SetStreamContentAndContentType(body, *contentType)
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ContentRequestBuilder when successful
func (m *ContentRequestBuilder) WithUrl(rawUrl string) *ContentRequestBuilder {
	return NewContentRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
