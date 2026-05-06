package search

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ContractRequestBuilder builds and executes requests for operations under \search\contract
type ContractRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// NewContractRequestBuilderInternal instantiates a new ContractRequestBuilder and sets the default values.
func NewContractRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContractRequestBuilder {
	m := &ContractRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/search/contract", pathParameters),
	}
	return m
}

// NewContractRequestBuilder instantiates a new ContractRequestBuilder and sets the default values.
func NewContractRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ContractRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewContractRequestBuilderInternal(urlParams, requestAdapter)
}

// Rules search for contract rules by tag.
// returns a *ContractRulesRequestBuilder when successful
func (m *ContractRequestBuilder) Rules() *ContractRulesRequestBuilder {
	return NewContractRulesRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
