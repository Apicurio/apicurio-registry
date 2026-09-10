package wellknown

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ArdExploreRequestBuilder aRD explore.
type ArdExploreRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ArdExploreRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ArdExploreRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewArdExploreRequestBuilderInternal instantiates a new ArdExploreRequestBuilder and sets the default values.
func NewArdExploreRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ArdExploreRequestBuilder {
	m := &ArdExploreRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/ard/explore", pathParameters),
	}
	return m
}

// NewArdExploreRequestBuilder instantiates a new ArdExploreRequestBuilder and sets the default values.
func NewArdExploreRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ArdExploreRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewArdExploreRequestBuilderInternal(urlParams, requestAdapter)
}

// Post aRD explore
// returns a ArdExploreResponseable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ArdExploreRequestBuilder) Post(ctx context.Context, body iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArdExploreRequestable, requestConfiguration *ArdExploreRequestBuilderPostRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArdExploreResponseable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateArdExploreResponseFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArdExploreResponseable), nil
}

// ToPostRequestInformation aRD explore
// returns a *RequestInformation when successful
func (m *ArdExploreRequestBuilder) ToPostRequestInformation(ctx context.Context, body iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArdExploreRequestable, requestConfiguration *ArdExploreRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
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
// returns a *ArdExploreRequestBuilder when successful
func (m *ArdExploreRequestBuilder) WithUrl(rawUrl string) *ArdExploreRequestBuilder {
	return NewArdExploreRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
