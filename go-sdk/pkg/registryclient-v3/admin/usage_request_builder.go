package admin

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// UsageRequestBuilder builds and executes requests for operations under \admin\usage
type UsageRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// Artifacts the artifacts property
// returns a *UsageArtifactsRequestBuilder when successful
func (m *UsageRequestBuilder) Artifacts() *UsageArtifactsRequestBuilder {
	return NewUsageArtifactsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// NewUsageRequestBuilderInternal instantiates a new UsageRequestBuilder and sets the default values.
func NewUsageRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageRequestBuilder {
	m := &UsageRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/usage", pathParameters),
	}
	return m
}

// NewUsageRequestBuilder instantiates a new UsageRequestBuilder and sets the default values.
func NewUsageRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *UsageRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewUsageRequestBuilderInternal(urlParams, requestAdapter)
}

// Events report schema usage events from SerDes clients.
// returns a *UsageEventsRequestBuilder when successful
func (m *UsageRequestBuilder) Events() *UsageEventsRequestBuilder {
	return NewUsageEventsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Summary get global usage summary counts.
// returns a *UsageSummaryRequestBuilder when successful
func (m *UsageRequestBuilder) Summary() *UsageSummaryRequestBuilder {
	return NewUsageSummaryRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
