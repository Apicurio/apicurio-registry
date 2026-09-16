package wellknown

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ArdRequestBuilder builds and executes requests for operations under \well-known\ard
type ArdRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// Agents aRD list agents.
// returns a *ArdAgentsRequestBuilder when successful
func (m *ArdRequestBuilder) Agents() *ArdAgentsRequestBuilder {
	return NewArdAgentsRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// NewArdRequestBuilderInternal instantiates a new ArdRequestBuilder and sets the default values.
func NewArdRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ArdRequestBuilder {
	m := &ArdRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/ard", pathParameters),
	}
	return m
}

// NewArdRequestBuilder instantiates a new ArdRequestBuilder and sets the default values.
func NewArdRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ArdRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewArdRequestBuilderInternal(urlParams, requestAdapter)
}

// Explore aRD explore.
// returns a *ArdExploreRequestBuilder when successful
func (m *ArdRequestBuilder) Explore() *ArdExploreRequestBuilder {
	return NewArdExploreRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Search aRD search.
// returns a *ArdSearchRequestBuilder when successful
func (m *ArdRequestBuilder) Search() *ArdSearchRequestBuilder {
	return NewArdSearchRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
