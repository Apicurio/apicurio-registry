package groups

import (
	"context"
	i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemRulesWithRuleItemRequestBuilder manage the configuration of a single artifact rule.
type ItemArtifactsItemRulesWithRuleItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemRulesWithRuleItemRequestBuilderDeleteRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemRulesWithRuleItemRequestBuilderDeleteRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemArtifactsItemRulesWithRuleItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemRulesWithRuleItemRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemArtifactsItemRulesWithRuleItemRequestBuilderPutRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemRulesWithRuleItemRequestBuilderPutRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewItemArtifactsItemRulesWithRuleItemRequestBuilderInternal instantiates a new WithRuleItemRequestBuilder and sets the default values.
func NewItemArtifactsItemRulesWithRuleItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemRulesWithRuleItemRequestBuilder {
	m := &ItemArtifactsItemRulesWithRuleItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/rules/{rule}", pathParameters),
	}
	return m
}

// NewItemArtifactsItemRulesWithRuleItemRequestBuilder instantiates a new WithRuleItemRequestBuilder and sets the default values.
func NewItemArtifactsItemRulesWithRuleItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemRulesWithRuleItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemRulesWithRuleItemRequestBuilderInternal(urlParams, requestAdapter)
}

// Delete deletes a rule from the artifact.  This results in the rule no longer applying forthis artifact.  If this is the only rule configured for the artifact, this is the same as deleting **all** rules, and the globally configured rules now apply tothis artifact.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemRulesWithRuleItemRequestBuilder) Delete(ctx context.Context, requestConfiguration *ItemArtifactsItemRulesWithRuleItemRequestBuilderDeleteRequestConfiguration) error {
	requestInfo, err := m.ToDeleteRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
		"500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// Get returns information about a single rule configured for an artifact.  This is usefulwhen you want to know what the current configuration settings are for a specific rule.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemRulesWithRuleItemRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemRulesWithRuleItemRequestBuilderGetRequestConfiguration) (i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.Ruleable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
		"500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateRuleFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.Ruleable), nil
}

// Put updates the configuration of a single rule for the artifact.  The configuration datais specific to each rule type, so the configuration of the `COMPATIBILITY` rule is in a different format from the configuration of the `VALIDITY` rule.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemRulesWithRuleItemRequestBuilder) Put(ctx context.Context, body i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.Ruleable, requestConfiguration *ItemArtifactsItemRulesWithRuleItemRequestBuilderPutRequestConfiguration) (i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.Ruleable, error) {
	requestInfo, err := m.ToPutRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
		"500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateRuleFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.Ruleable), nil
}

// ToDeleteRequestInformation deletes a rule from the artifact.  This results in the rule no longer applying forthis artifact.  If this is the only rule configured for the artifact, this is the same as deleting **all** rules, and the globally configured rules now apply tothis artifact.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemRulesWithRuleItemRequestBuilder) ToDeleteRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemRulesWithRuleItemRequestBuilderDeleteRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.DELETE, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToGetRequestInformation returns information about a single rule configured for an artifact.  This is usefulwhen you want to know what the current configuration settings are for a specific rule.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemRulesWithRuleItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemRulesWithRuleItemRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToPutRequestInformation updates the configuration of a single rule for the artifact.  The configuration datais specific to each rule type, so the configuration of the `COMPATIBILITY` rule is in a different format from the configuration of the `VALIDITY` rule.This operation can fail for the following reasons:* No artifact with this `artifactId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemRulesWithRuleItemRequestBuilder) ToPutRequestInformation(ctx context.Context, body i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.Ruleable, requestConfiguration *ItemArtifactsItemRulesWithRuleItemRequestBuilderPutRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.PUT, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
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
func (m *ItemArtifactsItemRulesWithRuleItemRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemRulesWithRuleItemRequestBuilder {
	return NewItemArtifactsItemRulesWithRuleItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
