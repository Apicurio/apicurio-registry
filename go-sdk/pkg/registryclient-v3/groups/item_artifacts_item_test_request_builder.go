package groups

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemTestRequestBuilder test whether content would pass update rules.
type ItemArtifactsItemTestRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemTestRequestBuilderPutRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemTestRequestBuilderPutRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewItemArtifactsItemTestRequestBuilderInternal instantiates a new TestRequestBuilder and sets the default values.
func NewItemArtifactsItemTestRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemTestRequestBuilder {
	m := &ItemArtifactsItemTestRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/test", pathParameters),
	}
	return m
}

// NewItemArtifactsItemTestRequestBuilder instantiates a new TestRequestBuilder and sets the default values.
func NewItemArtifactsItemTestRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemTestRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemTestRequestBuilderInternal(urlParams, requestAdapter)
}

// Put tests whether an update to the artifact's content *would* succeed for the provided content.Ultimately, this applies any rules configured for the artifact against the given contentto determine whether the rules would pass or fail, but without actually updating the artifactcontent.The body of the request should be the raw content of the artifact.  This is typically in JSON format for *most* of the supported types, but may be in another format for a few (for example, `PROTOBUF`).The update could fail for a number of reasons including:* Provided content (request body) was empty (HTTP error `400`)* No artifact with the `artifactId` exists (HTTP error `404`)* The new content violates one of the rules configured for the artifact (HTTP error `409`)* The provided artifact type is not recognized (HTTP error `404`)* A server error occurred (HTTP error `500`)When successful, this operation simply returns a *No Content* response.  This responseindicates that the content is valid against the configured content rules for the artifact (or the global rules if no artifact rules are enabled).
func (m *ItemArtifactsItemTestRequestBuilder) Put(ctx context.Context, body []byte, contentType *string, requestConfiguration *ItemArtifactsItemTestRequestBuilderPutRequestConfiguration) error {
	requestInfo, err := m.ToPutRequestInformation(ctx, body, contentType, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"409": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// ToPutRequestInformation tests whether an update to the artifact's content *would* succeed for the provided content.Ultimately, this applies any rules configured for the artifact against the given contentto determine whether the rules would pass or fail, but without actually updating the artifactcontent.The body of the request should be the raw content of the artifact.  This is typically in JSON format for *most* of the supported types, but may be in another format for a few (for example, `PROTOBUF`).The update could fail for a number of reasons including:* Provided content (request body) was empty (HTTP error `400`)* No artifact with the `artifactId` exists (HTTP error `404`)* The new content violates one of the rules configured for the artifact (HTTP error `409`)* The provided artifact type is not recognized (HTTP error `404`)* A server error occurred (HTTP error `500`)When successful, this operation simply returns a *No Content* response.  This responseindicates that the content is valid against the configured content rules for the artifact (or the global rules if no artifact rules are enabled).
func (m *ItemArtifactsItemTestRequestBuilder) ToPutRequestInformation(ctx context.Context, body []byte, contentType *string, requestConfiguration *ItemArtifactsItemTestRequestBuilderPutRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.PUT, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	requestInfo.SetStreamContentAndContentType(body, *contentType)
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *ItemArtifactsItemTestRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemTestRequestBuilder {
	return NewItemArtifactsItemTestRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
