package admin

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ImportRequestBuilder provides a way to import data into the registry.
type ImportRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ImportRequestBuilderPostQueryParameters imports registry data that was previously exported using the `/admin/export` operation.
type ImportRequestBuilderPostQueryParameters struct {
	// Query parameter indicating whether the registry must be empty before allowingdata to be imported.  Defaults to `true` if omitted.
	RequireEmptyRegistry *bool `uriparametername:"requireEmptyRegistry"`
}

// ImportRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ImportRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ImportRequestBuilderPostQueryParameters
}

// NewImportRequestBuilderInternal instantiates a new ImportRequestBuilder and sets the default values.
func NewImportRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ImportRequestBuilder {
	m := &ImportRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/import{?requireEmptyRegistry*}", pathParameters),
	}
	return m
}

// NewImportRequestBuilder instantiates a new ImportRequestBuilder and sets the default values.
func NewImportRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ImportRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewImportRequestBuilderInternal(urlParams, requestAdapter)
}

// Post imports registry data that was previously exported using the `/admin/export` operation.
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ImportRequestBuilder) Post(ctx context.Context, body []byte, requestConfiguration *ImportRequestBuilderPostRequestConfiguration) error {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"409": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// ToPostRequestInformation imports registry data that was previously exported using the `/admin/export` operation.
// returns a *RequestInformation when successful
func (m *ImportRequestBuilder) ToPostRequestInformation(ctx context.Context, body []byte, requestConfiguration *ImportRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		if requestConfiguration.QueryParameters != nil {
			requestInfo.AddQueryParameters(*(requestConfiguration.QueryParameters))
		}
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	requestInfo.SetStreamContentAndContentType(body, "application/zip")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ImportRequestBuilder when successful
func (m *ImportRequestBuilder) WithUrl(rawUrl string) *ImportRequestBuilder {
	return NewImportRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
