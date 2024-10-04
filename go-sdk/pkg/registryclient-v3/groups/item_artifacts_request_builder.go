package groups

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsRequestBuilder manage the collection of artifacts within a single group in the registry.
type ItemArtifactsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsRequestBuilderDeleteRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsRequestBuilderDeleteRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemArtifactsRequestBuilderGetQueryParameters returns a list of all artifacts in the group.  This list is paged.
type ItemArtifactsRequestBuilderGetQueryParameters struct {
	// The number of artifacts to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of artifacts to skip before starting the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	// Deprecated: This property is deprecated, use orderAsSortOrder instead
	Order *string `uriparametername:"order"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	OrderAsSortOrder *i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.SortOrder `uriparametername:"order"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	// Deprecated: This property is deprecated, use orderbyAsArtifactSortBy instead
	Orderby *string `uriparametername:"orderby"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	OrderbyAsArtifactSortBy *i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactSortBy `uriparametername:"orderby"`
}

// ItemArtifactsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ItemArtifactsRequestBuilderGetQueryParameters
}

// ItemArtifactsRequestBuilderPostQueryParameters creates a new artifact.  The body of the request should be a `CreateArtifact` object, which includes the metadata of the new artifact and, optionally, the metadata and content of the first version.If the artifact type is not provided, the registry attempts to figure out what kind of artifact is being added from thefollowing supported list:* Avro (`AVRO`)* Protobuf (`PROTOBUF`)* JSON Schema (`JSON`)* Kafka Connect (`KCONNECT`)* OpenAPI (`OPENAPI`)* AsyncAPI (`ASYNCAPI`)* GraphQL (`GRAPHQL`)* Web Services Description Language (`WSDL`)* XML Schema (`XSD`)An artifact will be created using the unique artifact ID that can optionally be provided in the request body.  If not provided in the request, the server willgenerate a unique ID for the artifact.  It is typically recommended that callersprovide the ID, because it is typically a meaningful identifier, and as suchfor most use cases should be supplied by the caller.If an artifact with the provided artifact ID already exists, the default behavioris for the server to reject the content with a 409 error.  However, the caller cansupply the `ifExists` query parameter to alter this default behavior. The `ifExists`query parameter can have one of the following values:* `FAIL` (*default*) - server rejects the content with a 409 error* `UPDATE` - server updates the existing artifact and returns the new metadata* `RETURN` - server does not create or add content to the server, but instead returns the metadata for the existing artifact* `RETURN_OR_UPDATE` - server returns an existing **version** that matches the provided content if such a version exists, otherwise a new version is createdThis operation may fail for one of the following reasons:* An invalid `ArtifactType` was indicated (HTTP error `400`)* No `ArtifactType` was indicated and the server could not determine one from the content (HTTP error `400`)* Provided content (request body) was empty (HTTP error `400`)* An invalid version number was used for the optional included first version (HTTP error `400`)* An artifact with the provided ID already exists (HTTP error `409`)* The content violates one of the configured global rules (HTTP error `409`)* A server error occurred (HTTP error `500`)Note that if the `dryRun` query parameter is set to `true`, then this operationwill not actually make any changes.  Instead it will succeed or fail based on whether it **would have worked**.  Use this option to, for example, check if anartifact is valid or if a new version passes configured compatibility checks.
type ItemArtifactsRequestBuilderPostQueryParameters struct {
	// Used only when the `ifExists` query parameter is set to `RETURN_OR_UPDATE`, this parameter can be set to `true` to indicate that the server should "canonicalize" the content when searching for a matching version.  The canonicalization algorithm is unique to each artifact type, but typically involves removing extra whitespace and formatting the content in a consistent manner.
	Canonical *bool `uriparametername:"canonical"`
	// When set to `true`, the operation will not result in any changes. Instead, itwill return a result based on whether the operation **would have succeeded**.
	DryRun *bool `uriparametername:"dryRun"`
	// Set this option to instruct the server on what to do if the artifact already exists.
	// Deprecated: This property is deprecated, use ifExistsAsIfArtifactExists instead
	IfExists *string `uriparametername:"ifExists"`
	// Set this option to instruct the server on what to do if the artifact already exists.
	IfExistsAsIfArtifactExists *i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.IfArtifactExists `uriparametername:"ifExists"`
}

// ItemArtifactsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ItemArtifactsRequestBuilderPostQueryParameters
}

// ByArtifactId manage a single artifact.
func (m *ItemArtifactsRequestBuilder) ByArtifactId(artifactId string) *ItemArtifactsWithArtifactItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if artifactId != "" {
		urlTplParams["artifactId"] = artifactId
	}
	return NewItemArtifactsWithArtifactItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewItemArtifactsRequestBuilderInternal instantiates a new ArtifactsRequestBuilder and sets the default values.
func NewItemArtifactsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsRequestBuilder {
	m := &ItemArtifactsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts{?limit*,offset*,order*,orderby*,ifExists*,canonical*,dryRun*}", pathParameters),
	}
	return m
}

// NewItemArtifactsRequestBuilder instantiates a new ArtifactsRequestBuilder and sets the default values.
func NewItemArtifactsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsRequestBuilderInternal(urlParams, requestAdapter)
}

// Delete deletes all of the artifacts that exist in a given group.
func (m *ItemArtifactsRequestBuilder) Delete(ctx context.Context, requestConfiguration *ItemArtifactsRequestBuilderDeleteRequestConfiguration) error {
	requestInfo, err := m.ToDeleteRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// Get returns a list of all artifacts in the group.  This list is paged.
func (m *ItemArtifactsRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsRequestBuilderGetRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactSearchResultsable), nil
}

// Post creates a new artifact.  The body of the request should be a `CreateArtifact` object, which includes the metadata of the new artifact and, optionally, the metadata and content of the first version.If the artifact type is not provided, the registry attempts to figure out what kind of artifact is being added from thefollowing supported list:* Avro (`AVRO`)* Protobuf (`PROTOBUF`)* JSON Schema (`JSON`)* Kafka Connect (`KCONNECT`)* OpenAPI (`OPENAPI`)* AsyncAPI (`ASYNCAPI`)* GraphQL (`GRAPHQL`)* Web Services Description Language (`WSDL`)* XML Schema (`XSD`)An artifact will be created using the unique artifact ID that can optionally be provided in the request body.  If not provided in the request, the server willgenerate a unique ID for the artifact.  It is typically recommended that callersprovide the ID, because it is typically a meaningful identifier, and as suchfor most use cases should be supplied by the caller.If an artifact with the provided artifact ID already exists, the default behavioris for the server to reject the content with a 409 error.  However, the caller cansupply the `ifExists` query parameter to alter this default behavior. The `ifExists`query parameter can have one of the following values:* `FAIL` (*default*) - server rejects the content with a 409 error* `UPDATE` - server updates the existing artifact and returns the new metadata* `RETURN` - server does not create or add content to the server, but instead returns the metadata for the existing artifact* `RETURN_OR_UPDATE` - server returns an existing **version** that matches the provided content if such a version exists, otherwise a new version is createdThis operation may fail for one of the following reasons:* An invalid `ArtifactType` was indicated (HTTP error `400`)* No `ArtifactType` was indicated and the server could not determine one from the content (HTTP error `400`)* Provided content (request body) was empty (HTTP error `400`)* An invalid version number was used for the optional included first version (HTTP error `400`)* An artifact with the provided ID already exists (HTTP error `409`)* The content violates one of the configured global rules (HTTP error `409`)* A server error occurred (HTTP error `500`)Note that if the `dryRun` query parameter is set to `true`, then this operationwill not actually make any changes.  Instead it will succeed or fail based on whether it **would have worked**.  Use this option to, for example, check if anartifact is valid or if a new version passes configured compatibility checks.
func (m *ItemArtifactsRequestBuilder) Post(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactable, requestConfiguration *ItemArtifactsRequestBuilderPostRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactResponseable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
		"409": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateCreateArtifactResponseFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactResponseable), nil
}

// ToDeleteRequestInformation deletes all of the artifacts that exist in a given group.
func (m *ItemArtifactsRequestBuilder) ToDeleteRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsRequestBuilderDeleteRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.DELETE, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToGetRequestInformation returns a list of all artifacts in the group.  This list is paged.
func (m *ItemArtifactsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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

// ToPostRequestInformation creates a new artifact.  The body of the request should be a `CreateArtifact` object, which includes the metadata of the new artifact and, optionally, the metadata and content of the first version.If the artifact type is not provided, the registry attempts to figure out what kind of artifact is being added from thefollowing supported list:* Avro (`AVRO`)* Protobuf (`PROTOBUF`)* JSON Schema (`JSON`)* Kafka Connect (`KCONNECT`)* OpenAPI (`OPENAPI`)* AsyncAPI (`ASYNCAPI`)* GraphQL (`GRAPHQL`)* Web Services Description Language (`WSDL`)* XML Schema (`XSD`)An artifact will be created using the unique artifact ID that can optionally be provided in the request body.  If not provided in the request, the server willgenerate a unique ID for the artifact.  It is typically recommended that callersprovide the ID, because it is typically a meaningful identifier, and as suchfor most use cases should be supplied by the caller.If an artifact with the provided artifact ID already exists, the default behavioris for the server to reject the content with a 409 error.  However, the caller cansupply the `ifExists` query parameter to alter this default behavior. The `ifExists`query parameter can have one of the following values:* `FAIL` (*default*) - server rejects the content with a 409 error* `UPDATE` - server updates the existing artifact and returns the new metadata* `RETURN` - server does not create or add content to the server, but instead returns the metadata for the existing artifact* `RETURN_OR_UPDATE` - server returns an existing **version** that matches the provided content if such a version exists, otherwise a new version is createdThis operation may fail for one of the following reasons:* An invalid `ArtifactType` was indicated (HTTP error `400`)* No `ArtifactType` was indicated and the server could not determine one from the content (HTTP error `400`)* Provided content (request body) was empty (HTTP error `400`)* An invalid version number was used for the optional included first version (HTTP error `400`)* An artifact with the provided ID already exists (HTTP error `409`)* The content violates one of the configured global rules (HTTP error `409`)* A server error occurred (HTTP error `500`)Note that if the `dryRun` query parameter is set to `true`, then this operationwill not actually make any changes.  Instead it will succeed or fail based on whether it **would have worked**.  Use this option to, for example, check if anartifact is valid or if a new version passes configured compatibility checks.
func (m *ItemArtifactsRequestBuilder) ToPostRequestInformation(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactable, requestConfiguration *ItemArtifactsRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
func (m *ItemArtifactsRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsRequestBuilder {
	return NewItemArtifactsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
