package search

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
)

// FederatedRequestBuilder builds and executes requests for operations under \search\federated
type FederatedRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// FederatedRequestBuilderGetQueryParameters searches for AI agent cards (Artifacts) across the local registry and all configured peer registries.
type FederatedRequestBuilderGetQueryParameters struct {
    // Filter by artifactId.
    ArtifactId *string `uriparametername:"artifactId"`
    // Filter by artifact type (`AVRO`, `JSON`, etc).
    ArtifactType *string `uriparametername:"artifactType"`
    // Filter by contentId.
    ContentId *int64 `uriparametername:"contentId"`
    // Filter by description.
    Description *string `uriparametername:"description"`
    // Filter by globalId.
    GlobalId *int64 `uriparametername:"globalId"`
    // Filter by artifact group.
    GroupId *string `uriparametername:"groupId"`
    // Filter by one or more name/value label.  Separate each name/value pair using a colon.  Forexample `labels=foo:bar` will return only artifacts with a label named `foo`and value `bar`.
    Labels []string `uriparametername:"labels"`
    // The number of artifacts to return.  Defaults to 20.
    Limit *int32 `uriparametername:"limit"`
    // Filter by artifact name.
    Name *string `uriparametername:"name"`
    // The number of artifacts to skip before starting to collect the result set.  Defaults to 0.
    Offset *int32 `uriparametername:"offset"`
    // Sort order, ascending (`asc`) or descending (`desc`).
    // Deprecated: This property is deprecated, use OrderAsSortOrder instead
    Order *string `uriparametername:"order"`
    // Sort order, ascending (`asc`) or descending (`desc`).
    OrderAsSortOrder *iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.SortOrder `uriparametername:"order"`
    // The field to sort by.  Can be one of:* `name`* `createdOn`
    // Deprecated: This property is deprecated, use OrderbyAsArtifactSortBy instead
    Orderby *string `uriparametername:"orderby"`
    // The field to sort by.  Can be one of:* `name`* `createdOn`
    OrderbyAsArtifactSortBy *iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSortBy `uriparametername:"orderby"`
    // Indicates whether to skip the total count query.  When true, the total count is not computed and count will be 0 in the response.  This can improve performance for large datasets.
    SkipCount *bool `uriparametername:"skipCount"`
}
// FederatedRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type FederatedRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
    // Request query parameters
    QueryParameters *FederatedRequestBuilderGetQueryParameters
}
// NewFederatedRequestBuilderInternal instantiates a new FederatedRequestBuilder and sets the default values.
func NewFederatedRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*FederatedRequestBuilder) {
    m := &FederatedRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/search/federated{?artifactId*,artifactType*,contentId*,description*,globalId*,groupId*,labels*,limit*,name*,offset*,order*,orderby*,skipCount*}", pathParameters),
    }
    return m
}
// NewFederatedRequestBuilder instantiates a new FederatedRequestBuilder and sets the default values.
func NewFederatedRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*FederatedRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewFederatedRequestBuilderInternal(urlParams, requestAdapter)
}
// Get searches for AI agent cards (Artifacts) across the local registry and all configured peer registries.
// returns a ArtifactSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 401 status code
// returns a ProblemDetails error when the service returns a 403 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *FederatedRequestBuilder) Get(ctx context.Context, requestConfiguration *FederatedRequestBuilderGetRequestConfiguration)(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSearchResultsable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "401": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "403": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateArtifactSearchResultsFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.ArtifactSearchResultsable), nil
}
// ToGetRequestInformation searches for AI agent cards (Artifacts) across the local registry and all configured peer registries.
// returns a *RequestInformation when successful
func (m *FederatedRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *FederatedRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *FederatedRequestBuilder when successful
func (m *FederatedRequestBuilder) WithUrl(rawUrl string)(*FederatedRequestBuilder) {
    return NewFederatedRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
