package groups

import (
	"context"
	iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemVersionsItemExportRequestBuilder export a Protobuf artifact version with dependencies as a ZIP file.
type ItemArtifactsItemVersionsItemExportRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemVersionsItemExportRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemVersionsItemExportRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewItemArtifactsItemVersionsItemExportRequestBuilderInternal instantiates a new ItemArtifactsItemVersionsItemExportRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemExportRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsItemExportRequestBuilder {
	m := &ItemArtifactsItemVersionsItemExportRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/export", pathParameters),
	}
	return m
}

// NewItemArtifactsItemVersionsItemExportRequestBuilder instantiates a new ItemArtifactsItemVersionsItemExportRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemExportRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsItemExportRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemVersionsItemExportRequestBuilderInternal(urlParams, requestAdapter)
}

// Get exports a Protobuf artifact version with all its transitive dependencies as a ZIP file.The files in the ZIP are organized by their package names to match the expected directorystructure for protoc compilation. Import statements are rewritten to use canonicalpackage-based paths.This operation is only supported for PROTOBUF artifact types.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* Artifact is not of type PROTOBUF (HTTP error `400`)* A server error occurred (HTTP error `500`)
// returns a []byte when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemVersionsItemExportRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsItemExportRequestBuilderGetRequestConfiguration) ([]byte, error) {
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
	res, err := m.BaseRequestBuilder.RequestAdapter.SendPrimitive(ctx, requestInfo, "[]byte", errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.([]byte), nil
}

// ToGetRequestInformation exports a Protobuf artifact version with all its transitive dependencies as a ZIP file.The files in the ZIP are organized by their package names to match the expected directorystructure for protoc compilation. Import statements are rewritten to use canonicalpackage-based paths.This operation is only supported for PROTOBUF artifact types.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No version with this `version` exists (HTTP error `404`)* Artifact is not of type PROTOBUF (HTTP error `400`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemVersionsItemExportRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemVersionsItemExportRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/zip, application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ItemArtifactsItemVersionsItemExportRequestBuilder when successful
func (m *ItemArtifactsItemVersionsItemExportRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemVersionsItemExportRequestBuilder {
	return NewItemArtifactsItemVersionsItemExportRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
