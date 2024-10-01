package admin

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// SnapshotsRequestBuilder triggers a snapshot of the Registry storage. Only supported in KafkaSQL storage
type SnapshotsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// SnapshotsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type SnapshotsRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewSnapshotsRequestBuilderInternal instantiates a new SnapshotsRequestBuilder and sets the default values.
func NewSnapshotsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *SnapshotsRequestBuilder {
	m := &SnapshotsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/snapshots", pathParameters),
	}
	return m
}

// NewSnapshotsRequestBuilder instantiates a new SnapshotsRequestBuilder and sets the default values.
func NewSnapshotsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *SnapshotsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewSnapshotsRequestBuilderInternal(urlParams, requestAdapter)
}

// Post triggers the creation of a snapshot of the internal database for compatible storages.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a SnapshotMetaDataable when successful
// returns a ProblemDetails error when the service returns a 500 status code
func (m *SnapshotsRequestBuilder) Post(ctx context.Context, requestConfiguration *SnapshotsRequestBuilderPostRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.SnapshotMetaDataable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateSnapshotMetaDataFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.SnapshotMetaDataable), nil
}

// ToPostRequestInformation triggers the creation of a snapshot of the internal database for compatible storages.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *SnapshotsRequestBuilder) ToPostRequestInformation(ctx context.Context, requestConfiguration *SnapshotsRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *SnapshotsRequestBuilder when successful
func (m *SnapshotsRequestBuilder) WithUrl(rawUrl string) *SnapshotsRequestBuilder {
	return NewSnapshotsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
