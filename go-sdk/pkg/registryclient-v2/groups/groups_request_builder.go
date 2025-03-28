package groups

import (
	"context"
	idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// GroupsRequestBuilder collection of the groups in the registry.
type GroupsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// GroupsRequestBuilderGetQueryParameters returns a list of all groups.  This list is paged.
type GroupsRequestBuilderGetQueryParameters struct {
	// The number of groups to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of groups to skip before starting the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	// Deprecated: This property is deprecated, use OrderAsSortOrder instead
	Order *string `uriparametername:"order"`
	// Sort order, ascending (`asc`) or descending (`desc`).
	OrderAsSortOrder *idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.SortOrder `uriparametername:"order"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	// Deprecated: This property is deprecated, use OrderbyAsSortBy instead
	Orderby *string `uriparametername:"orderby"`
	// The field to sort by.  Can be one of:* `name`* `createdOn`
	OrderbyAsSortBy *idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.SortBy `uriparametername:"orderby"`
}

// GroupsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type GroupsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *GroupsRequestBuilderGetQueryParameters
}

// GroupsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type GroupsRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ByGroupId collection to manage a single group in the registry.
// returns a *WithGroupItemRequestBuilder when successful
func (m *GroupsRequestBuilder) ByGroupId(groupId string) *WithGroupItemRequestBuilder {
	urlTplParams := make(map[string]string)
	for idx, item := range m.BaseRequestBuilder.PathParameters {
		urlTplParams[idx] = item
	}
	if groupId != "" {
		urlTplParams["groupId"] = groupId
	}
	return NewWithGroupItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}

// NewGroupsRequestBuilderInternal instantiates a new GroupsRequestBuilder and sets the default values.
func NewGroupsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GroupsRequestBuilder {
	m := &GroupsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups{?limit*,offset*,order*,orderby*}", pathParameters),
	}
	return m
}

// NewGroupsRequestBuilder instantiates a new GroupsRequestBuilder and sets the default values.
func NewGroupsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GroupsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewGroupsRequestBuilderInternal(urlParams, requestAdapter)
}

// Get returns a list of all groups.  This list is paged.
// returns a GroupSearchResultsable when successful
// returns a Error error when the service returns a 500 status code
func (m *GroupsRequestBuilder) Get(ctx context.Context, requestConfiguration *GroupsRequestBuilderGetRequestConfiguration) (idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.GroupSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateGroupSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.GroupSearchResultsable), nil
}

// Post creates a new group.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)* The group already exist (HTTP error `409`)
// returns a GroupMetaDataable when successful
// returns a Error error when the service returns a 409 status code
// returns a Error error when the service returns a 500 status code
func (m *GroupsRequestBuilder) Post(ctx context.Context, body idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateGroupMetaDataable, requestConfiguration *GroupsRequestBuilderPostRequestConfiguration) (idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.GroupMetaDataable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"409": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
		"500": idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateGroupMetaDataFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.GroupMetaDataable), nil
}

// ToGetRequestInformation returns a list of all groups.  This list is paged.
// returns a *RequestInformation when successful
func (m *GroupsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *GroupsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		if requestConfiguration.QueryParameters != nil {
			requestInfo.AddQueryParameters(*(requestConfiguration.QueryParameters))
		}
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToPostRequestInformation creates a new group.This operation can fail for the following reasons:* A server error occurred (HTTP error `500`)* The group already exist (HTTP error `409`)
// returns a *RequestInformation when successful
func (m *GroupsRequestBuilder) ToPostRequestInformation(ctx context.Context, body idce6df71aec15bcaff7e717920c74a6e040e4229e56d54210ada4a689f7afc23.CreateGroupMetaDataable, requestConfiguration *GroupsRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
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
// returns a *GroupsRequestBuilder when successful
func (m *GroupsRequestBuilder) WithUrl(rawUrl string) *GroupsRequestBuilder {
	return NewGroupsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
