package groups

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemContractStatusRequestBuilder transition the contract lifecycle status.
type ItemArtifactsItemContractStatusRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemContractStatusRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemContractStatusRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewItemArtifactsItemContractStatusRequestBuilderInternal instantiates a new ItemArtifactsItemContractStatusRequestBuilder and sets the default values.
func NewItemArtifactsItemContractStatusRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemContractStatusRequestBuilder {
	m := &ItemArtifactsItemContractStatusRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/contract/status", pathParameters),
	}
	return m
}

// NewItemArtifactsItemContractStatusRequestBuilder instantiates a new ItemArtifactsItemContractStatusRequestBuilder and sets the default values.
func NewItemArtifactsItemContractStatusRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemContractStatusRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemContractStatusRequestBuilderInternal(urlParams, requestAdapter)
}

// Post transitions the contract status. Valid transitions: DRAFT to STABLE, DRAFT to DEPRECATED, STABLE to DEPRECATED. Reverse transitions are not allowed.
// returns a ContractMetadataable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemContractStatusRequestBuilder) Post(ctx context.Context, body iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ContractStatusTransitionable, requestConfiguration *ItemArtifactsItemContractStatusRequestBuilderPostRequestConfiguration) (iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ContractMetadataable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"404": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"409": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
		"500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateContractMetadataFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ContractMetadataable), nil
}

// ToPostRequestInformation transitions the contract status. Valid transitions: DRAFT to STABLE, DRAFT to DEPRECATED, STABLE to DEPRECATED. Reverse transitions are not allowed.
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemContractStatusRequestBuilder) ToPostRequestInformation(ctx context.Context, body iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ContractStatusTransitionable, requestConfiguration *ItemArtifactsItemContractStatusRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *ItemArtifactsItemContractStatusRequestBuilder when successful
func (m *ItemArtifactsItemContractStatusRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemContractStatusRequestBuilder {
	return NewItemArtifactsItemContractStatusRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
