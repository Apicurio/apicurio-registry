package ids

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ContentHashesWithContentHashItemRequestBuilder access artifact content utilizing the SHA-256 hash of the content.
type ContentHashesWithContentHashItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ContentHashesWithContentHashItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ContentHashesWithContentHashItemRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewContentHashesWithContentHashItemRequestBuilderInternal instantiates a new WithContentHashItemRequestBuilder and sets the default values.
func NewContentHashesWithContentHashItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentHashesWithContentHashItemRequestBuilder {
	m := &ContentHashesWithContentHashItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/contentHashes/{contentHash}", pathParameters),
	}
	return m
}

// NewContentHashesWithContentHashItemRequestBuilder instantiates a new WithContentHashItemRequestBuilder and sets the default values.
func NewContentHashesWithContentHashItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentHashesWithContentHashItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContentHashesWithContentHashItemRequestBuilderInternal(urlParams, requestAdapter)
}

// Get gets the content for an artifact version in the registry using the SHA-256 hash of the content.  This content hash may be shared by multiple artifactversions in the case where the artifact versions have identical content.This operation may fail for one of the following reasons:* No content with this `contentHash` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ContentHashesWithContentHashItemRequestBuilder) Get(ctx context.Context, requestConfiguration *ContentHashesWithContentHashItemRequestBuilderGetRequestConfiguration) ([]byte, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
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
func (m *ContentHashesWithContentHashItemRequestBuilder) References() *ContentHashesItemReferencesRequestBuilder {
	return NewContentHashesItemReferencesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// ToGetRequestInformation gets the content for an artifact version in the registry using the SHA-256 hash of the content.  This content hash may be shared by multiple artifactversions in the case where the artifact versions have identical content.This operation may fail for one of the following reasons:* No content with this `contentHash` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ContentHashesWithContentHashItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ContentHashesWithContentHashItemRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "*/*, application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *ContentHashesWithContentHashItemRequestBuilder) WithUrl(rawUrl string) *ContentHashesWithContentHashItemRequestBuilder {
	return NewContentHashesWithContentHashItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
