package groups

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemRulesWithRuleTypeItemRequestBuilder manage the configuration of a single group rule.
type ItemRulesWithRuleTypeItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemRulesWithRuleTypeItemRequestBuilderDeleteRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemRulesWithRuleTypeItemRequestBuilderDeleteRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemRulesWithRuleTypeItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemRulesWithRuleTypeItemRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemRulesWithRuleTypeItemRequestBuilderPutRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemRulesWithRuleTypeItemRequestBuilderPutRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewItemRulesWithRuleTypeItemRequestBuilderInternal instantiates a new WithRuleTypeItemRequestBuilder and sets the default values.
func NewItemRulesWithRuleTypeItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemRulesWithRuleTypeItemRequestBuilder {
	m := &ItemRulesWithRuleTypeItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/rules/{ruleType}", pathParameters),
	}
	return m
}

// NewItemRulesWithRuleTypeItemRequestBuilder instantiates a new WithRuleTypeItemRequestBuilder and sets the default values.
func NewItemRulesWithRuleTypeItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemRulesWithRuleTypeItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemRulesWithRuleTypeItemRequestBuilderInternal(urlParams, requestAdapter)
}

// Delete deletes a rule from the group.  This results in the rule no longer applying forthis group.  If this is the only rule configured for the group, this is the same as deleting **all** rules, and the globally configured rules now apply tothis group.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No rule with this name/type is configured for this group (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemRulesWithRuleTypeItemRequestBuilder) Delete(ctx context.Context, requestConfiguration *ItemRulesWithRuleTypeItemRequestBuilderDeleteRequestConfiguration) error {
	requestInfo, err := m.ToDeleteRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// Get returns information about a single rule configured for a group.  This is usefulwhen you want to know what the current configuration settings are for a specific rule.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemRulesWithRuleTypeItemRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemRulesWithRuleTypeItemRequestBuilderGetRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.Ruleable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateRuleFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.Ruleable), nil
}

// Put updates the configuration of a single rule for the group.  The configuration datais specific to each rule type, so the configuration of the `COMPATIBILITY` rule is in a different format from the configuration of the `VALIDITY` rule.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemRulesWithRuleTypeItemRequestBuilder) Put(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.Ruleable, requestConfiguration *ItemRulesWithRuleTypeItemRequestBuilderPutRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.Ruleable, error) {
	requestInfo, err := m.ToPutRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateRuleFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.Ruleable), nil
}

// ToDeleteRequestInformation deletes a rule from the group.  This results in the rule no longer applying forthis group.  If this is the only rule configured for the group, this is the same as deleting **all** rules, and the globally configured rules now apply tothis group.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No rule with this name/type is configured for this group (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemRulesWithRuleTypeItemRequestBuilder) ToDeleteRequestInformation(ctx context.Context, requestConfiguration *ItemRulesWithRuleTypeItemRequestBuilderDeleteRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.DELETE, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToGetRequestInformation returns information about a single rule configured for a group.  This is usefulwhen you want to know what the current configuration settings are for a specific rule.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemRulesWithRuleTypeItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemRulesWithRuleTypeItemRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToPutRequestInformation updates the configuration of a single rule for the group.  The configuration datais specific to each rule type, so the configuration of the `COMPATIBILITY` rule is in a different format from the configuration of the `VALIDITY` rule.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No rule with this name/type is configured for this artifact (HTTP error `404`)* Invalid rule type (HTTP error `400`)* A server error occurred (HTTP error `500`)
func (m *ItemRulesWithRuleTypeItemRequestBuilder) ToPutRequestInformation(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.Ruleable, requestConfiguration *ItemRulesWithRuleTypeItemRequestBuilderPutRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
func (m *ItemRulesWithRuleTypeItemRequestBuilder) WithUrl(rawUrl string) *ItemRulesWithRuleTypeItemRequestBuilder {
	return NewItemRulesWithRuleTypeItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
