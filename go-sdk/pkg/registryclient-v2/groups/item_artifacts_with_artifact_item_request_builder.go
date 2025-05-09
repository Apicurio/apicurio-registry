package groups

import (
	"context"
	idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsWithArtifactItemRequestBuilder manage a single artifact.
type ItemArtifactsWithArtifactItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsWithArtifactItemRequestBuilderDeleteRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsWithArtifactItemRequestBuilderDeleteRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemArtifactsWithArtifactItemRequestBuilderGetQueryParameters returns the latest version of the artifact in its raw form.  The `Content-Type` of theresponse depends on the artifact type.  In most cases, this is `application/json`, but for some types it may be different (for example, `PROTOBUF`).If the latest version of the artifact is marked as `DISABLED`, the next available non-disabled version will be used.This operation may fail for one of the following reasons:* No artifact with this `artifactId` exists or all versions are `DISABLED` (HTTP error `404`)* A server error occurred (HTTP error `500`)
type ItemArtifactsWithArtifactItemRequestBuilderGetQueryParameters struct {
	// Allows the user to specify if the content should be dereferenced when being returned
	Dereference *bool `uriparametername:"dereference"`
}

// ItemArtifactsWithArtifactItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsWithArtifactItemRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ItemArtifactsWithArtifactItemRequestBuilderGetQueryParameters
}

// ItemArtifactsWithArtifactItemRequestBuilderPutRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsWithArtifactItemRequestBuilderPutRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewItemArtifactsWithArtifactItemRequestBuilderInternal instantiates a new ItemArtifactsWithArtifactItemRequestBuilder and sets the default values.
func NewItemArtifactsWithArtifactItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsWithArtifactItemRequestBuilder {
	m := &ItemArtifactsWithArtifactItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}{?dereference*}", pathParameters),
	}
	return m
}

// NewItemArtifactsWithArtifactItemRequestBuilder instantiates a new ItemArtifactsWithArtifactItemRequestBuilder and sets the default values.
func NewItemArtifactsWithArtifactItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsWithArtifactItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsWithArtifactItemRequestBuilderInternal(urlParams, requestAdapter)
}

// Delete deletes an artifact completely, resulting in all versions of the artifact also beingdeleted.  This may fail for one of the following reasons:* No artifact with the `artifactId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a Error error when the service returns a 404 status code
// returns a Error error when the service returns a 500 status code
func (m *ItemArtifactsWithArtifactItemRequestBuilder) Delete(ctx context.Context, requestConfiguration *ItemArtifactsWithArtifactItemRequestBuilderDeleteRequestConfiguration) error {
	requestInfo, err := m.ToDeleteRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
		"500": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// Get returns the latest version of the artifact in its raw form.  The `Content-Type` of theresponse depends on the artifact type.  In most cases, this is `application/json`, but for some types it may be different (for example, `PROTOBUF`).If the latest version of the artifact is marked as `DISABLED`, the next available non-disabled version will be used.This operation may fail for one of the following reasons:* No artifact with this `artifactId` exists or all versions are `DISABLED` (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a []byte when successful
// returns a Error error when the service returns a 404 status code
// returns a Error error when the service returns a 500 status code
func (m *ItemArtifactsWithArtifactItemRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsWithArtifactItemRequestBuilderGetRequestConfiguration) ([]byte, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
		"500": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
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

// Meta manage the metadata of a single artifact.
// returns a *ItemArtifactsItemMetaRequestBuilder when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) Meta() *ItemArtifactsItemMetaRequestBuilder {
	return NewItemArtifactsItemMetaRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Owner manage the ownership of a single artifact.
// returns a *ItemArtifactsItemOwnerRequestBuilder when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) Owner() *ItemArtifactsItemOwnerRequestBuilder {
	return NewItemArtifactsItemOwnerRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Put updates an artifact by uploading new content.  The body of the request canbe the raw content of the artifact or a JSON object containing both the raw content anda set of references to other artifacts..  This is typically in JSON format for *most*of the supported types, but may be in another format for a few (for example, `PROTOBUF`).The type of the content should be compatible with the artifact's type (it would bean error to update an `AVRO` artifact with new `OPENAPI` content, for example).The update could fail for a number of reasons including:* Provided content (request body) was empty (HTTP error `400`)* No artifact with the `artifactId` exists (HTTP error `404`)* The new content violates one of the rules configured for the artifact (HTTP error `409`)* A server error occurred (HTTP error `500`)When successful, this creates a new version of the artifact, making it the most recent(and therefore official) version of the artifact.
// returns a ArtifactMetaDataable when successful
// returns a Error error when the service returns a 404 status code
// returns a Error error when the service returns a 409 status code
// returns a Error error when the service returns a 500 status code
func (m *ItemArtifactsWithArtifactItemRequestBuilder) Put(ctx context.Context, body idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ArtifactContentable, requestConfiguration *ItemArtifactsWithArtifactItemRequestBuilderPutRequestConfiguration) (idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ArtifactMetaDataable, error) {
	requestInfo, err := m.ToPutRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
		"409": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
		"500": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateArtifactMetaDataFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ArtifactMetaDataable), nil
}

// Rules manage the rules for a single artifact.
// returns a *ItemArtifactsItemRulesRequestBuilder when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) Rules() *ItemArtifactsItemRulesRequestBuilder {
	return NewItemArtifactsItemRulesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// State manage the state of an artifact.
// returns a *ItemArtifactsItemStateRequestBuilder when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) State() *ItemArtifactsItemStateRequestBuilder {
	return NewItemArtifactsItemStateRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Test test whether content would pass update rules.
// returns a *ItemArtifactsItemTestRequestBuilder when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) Test() *ItemArtifactsItemTestRequestBuilder {
	return NewItemArtifactsItemTestRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// ToDeleteRequestInformation deletes an artifact completely, resulting in all versions of the artifact also beingdeleted.  This may fail for one of the following reasons:* No artifact with the `artifactId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) ToDeleteRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsWithArtifactItemRequestBuilderDeleteRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.DELETE, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToGetRequestInformation returns the latest version of the artifact in its raw form.  The `Content-Type` of theresponse depends on the artifact type.  In most cases, this is `application/json`, but for some types it may be different (for example, `PROTOBUF`).If the latest version of the artifact is marked as `DISABLED`, the next available non-disabled version will be used.This operation may fail for one of the following reasons:* No artifact with this `artifactId` exists or all versions are `DISABLED` (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsWithArtifactItemRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		if requestConfiguration.QueryParameters != nil {
			requestInfo.AddQueryParameters(*(requestConfiguration.QueryParameters))
		}
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "*/*, application/json")
	return requestInfo, nil
}

// ToPutRequestInformation updates an artifact by uploading new content.  The body of the request canbe the raw content of the artifact or a JSON object containing both the raw content anda set of references to other artifacts..  This is typically in JSON format for *most*of the supported types, but may be in another format for a few (for example, `PROTOBUF`).The type of the content should be compatible with the artifact's type (it would bean error to update an `AVRO` artifact with new `OPENAPI` content, for example).The update could fail for a number of reasons including:* Provided content (request body) was empty (HTTP error `400`)* No artifact with the `artifactId` exists (HTTP error `404`)* The new content violates one of the rules configured for the artifact (HTTP error `409`)* A server error occurred (HTTP error `500`)When successful, this creates a new version of the artifact, making it the most recent(and therefore official) version of the artifact.
// returns a *RequestInformation when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) ToPutRequestInformation(ctx context.Context, body idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.ArtifactContentable, requestConfiguration *ItemArtifactsWithArtifactItemRequestBuilderPutRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.PUT, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
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

// Versions manage all the versions of an artifact in the registry.
// returns a *ItemArtifactsItemVersionsRequestBuilder when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) Versions() *ItemArtifactsItemVersionsRequestBuilder {
	return NewItemArtifactsItemVersionsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ItemArtifactsWithArtifactItemRequestBuilder when successful
func (m *ItemArtifactsWithArtifactItemRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsWithArtifactItemRequestBuilder {
	return NewItemArtifactsWithArtifactItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
