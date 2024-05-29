package admin

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ConfigTriggerSnapshotRequestBuilder triggers a snapshot of the Registry storage. Only supported in KafkaSQL storage
type ConfigTriggerSnapshotRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ConfigTriggerSnapshotRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ConfigTriggerSnapshotRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewConfigTriggerSnapshotRequestBuilderInternal instantiates a new TriggerSnapshotRequestBuilder and sets the default values.
func NewConfigTriggerSnapshotRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ConfigTriggerSnapshotRequestBuilder {
	m := &ConfigTriggerSnapshotRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/config/triggerSnapshot", pathParameters),
	}
	return m
}

// NewConfigTriggerSnapshotRequestBuilder instantiates a new TriggerSnapshotRequestBuilder and sets the default values.
func NewConfigTriggerSnapshotRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ConfigTriggerSnapshotRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewConfigTriggerSnapshotRequestBuilderInternal(urlParams, requestAdapter)
}

// Get triggers the creation of a snapshot of the internal database for compatible storages.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
func (m *ConfigTriggerSnapshotRequestBuilder) Get(ctx context.Context, requestConfiguration *ConfigTriggerSnapshotRequestBuilderGetRequestConfiguration) ([]byte, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
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

// ToGetRequestInformation triggers the creation of a snapshot of the internal database for compatible storages.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)
func (m *ConfigTriggerSnapshotRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ConfigTriggerSnapshotRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
func (m *ConfigTriggerSnapshotRequestBuilder) WithUrl(rawUrl string) *ConfigTriggerSnapshotRequestBuilder {
	return NewConfigTriggerSnapshotRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
