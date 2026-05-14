package groups

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemVersionsItemContractRequestBuilder builds and executes requests for operations under \groups\{groupId}\artifacts\{artifactId}\versions\{versionExpression}\contract
type ItemArtifactsItemVersionsItemContractRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// NewItemArtifactsItemVersionsItemContractRequestBuilderInternal instantiates a new ItemArtifactsItemVersionsItemContractRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemContractRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsItemContractRequestBuilder {
	m := &ItemArtifactsItemVersionsItemContractRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/contract", pathParameters),
	}
	return m
}

// NewItemArtifactsItemVersionsItemContractRequestBuilder instantiates a new ItemArtifactsItemVersionsItemContractRequestBuilder and sets the default values.
func NewItemArtifactsItemVersionsItemContractRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemVersionsItemContractRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemVersionsItemContractRequestBuilderInternal(urlParams, requestAdapter)
}

// Ruleset manage contract ruleset for a specific artifact version.
// returns a *ItemArtifactsItemVersionsItemContractRulesetRequestBuilder when successful
func (m *ItemArtifactsItemVersionsItemContractRequestBuilder) Ruleset() *ItemArtifactsItemVersionsItemContractRulesetRequestBuilder {
	return NewItemArtifactsItemVersionsItemContractRulesetRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
