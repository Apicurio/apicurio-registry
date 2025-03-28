package ids

import (
	"context"
	idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ContentHashesItemWithContentHashRequestBuilder access artifact content utilizing the SHA-256 hash of the content.
type ContentHashesItemWithContentHashRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ContentHashesItemWithContentHashRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ContentHashesItemWithContentHashRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewContentHashesItemWithContentHashRequestBuilderInternal instantiates a new ContentHashesItemWithContentHashRequestBuilder and sets the default values.
func NewContentHashesItemWithContentHashRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter, contentHash *string) *ContentHashesItemWithContentHashRequestBuilder {
	m := &ContentHashesItemWithContentHashRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/ids/contentHashes/{contentHash}/", pathParameters),
	}
	if contentHash != nil {
		m.BaseRequestBuilder.PathParameters["contentHash"] = *contentHash
	}
	return m
}

// NewContentHashesItemWithContentHashRequestBuilder instantiates a new ContentHashesItemWithContentHashRequestBuilder and sets the default values.
func NewContentHashesItemWithContentHashRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContentHashesItemWithContentHashRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContentHashesItemWithContentHashRequestBuilderInternal(urlParams, requestAdapter, nil)
}

// Get gets the content for an artifact version in the registry using the SHA-256 hash of the content.  This content hash may be shared by multiple artifactversions in the case where the artifact versions have identical content.This operation may fail for one of the following reasons:* No content with this `contentHash` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a []byte when successful
// returns a Error error when the service returns a 404 status code
// returns a Error error when the service returns a 500 status code
func (m *ContentHashesItemWithContentHashRequestBuilder) Get(ctx context.Context, requestConfiguration *ContentHashesItemWithContentHashRequestBuilderGetRequestConfiguration) ([]byte, error) {
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

// ToGetRequestInformation gets the content for an artifact version in the registry using the SHA-256 hash of the content.  This content hash may be shared by multiple artifactversions in the case where the artifact versions have identical content.This operation may fail for one of the following reasons:* No content with this `contentHash` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ContentHashesItemWithContentHashRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ContentHashesItemWithContentHashRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "*/*, application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ContentHashesItemWithContentHashRequestBuilder when successful
func (m *ContentHashesItemWithContentHashRequestBuilder) WithUrl(rawUrl string) *ContentHashesItemWithContentHashRequestBuilder {
	return NewContentHashesItemWithContentHashRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
