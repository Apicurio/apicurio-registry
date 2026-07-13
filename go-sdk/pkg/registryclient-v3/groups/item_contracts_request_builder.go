package groups

import (
    "context"
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
    iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
)

// ItemContractsRequestBuilder manage ODCS data contracts within a group.
type ItemContractsRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ItemContractsRequestBuilderGetQueryParameters returns ODCS contracts in the specified group with pagination.
type ItemContractsRequestBuilderGetQueryParameters struct {
    // Maximum number of contracts to return (default 20, max 500).
    Limit *int32 `uriparametername:"limit"`
    // Number of contracts to skip (for pagination).
    Offset *int32 `uriparametername:"offset"`
}
// ItemContractsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemContractsRequestBuilderGetRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
    // Request query parameters
    QueryParameters *ItemContractsRequestBuilderGetQueryParameters
}
// ItemContractsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemContractsRequestBuilderPostRequestConfiguration struct {
    // Request headers
    Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
    // Request options
    Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}
// ByContractId manage a specific ODCS contract.
// returns a *ItemContractsWithContractItemRequestBuilder when successful
func (m *ItemContractsRequestBuilder) ByContractId(contractId string)(*ItemContractsWithContractItemRequestBuilder) {
    urlTplParams := make(map[string]string)
    for idx, item := range m.BaseRequestBuilder.PathParameters {
        urlTplParams[idx] = item
    }
    if contractId != "" {
        urlTplParams["contractId"] = contractId
    }
    return NewItemContractsWithContractItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}
// NewItemContractsRequestBuilderInternal instantiates a new ItemContractsRequestBuilder and sets the default values.
func NewItemContractsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemContractsRequestBuilder) {
    m := &ItemContractsRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/contracts{?limit*,offset*}", pathParameters),
    }
    return m
}
// NewItemContractsRequestBuilder instantiates a new ItemContractsRequestBuilder and sets the default values.
func NewItemContractsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*ItemContractsRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewItemContractsRequestBuilderInternal(urlParams, requestAdapter)
}
// Get returns ODCS contracts in the specified group with pagination.
// returns a []OdcsContractSummaryable when successful
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemContractsRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemContractsRequestBuilderGetRequestConfiguration)([]iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.OdcsContractSummaryable, error) {
    requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.SendCollection(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateOdcsContractSummaryFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    val := make([]iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.OdcsContractSummaryable, len(res))
    for i, v := range res {
        if v != nil {
            val[i] = v.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.OdcsContractSummaryable)
        }
    }
    return val, nil
}
// Post submits an ODCS v3.1 YAML contract. The contract is parsed, stored as an ODCS_CONTRACT artifact, and its contents are projected onto the referenced schema artifact (labels, rules, field tags).
// returns a OdcsContractResultable when successful
// returns a ProblemDetails error when the service returns a 400 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemContractsRequestBuilder) Post(ctx context.Context, body []byte, requestConfiguration *ItemContractsRequestBuilderPostRequestConfiguration)(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.OdcsContractResultable, error) {
    requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration);
    if err != nil {
        return nil, err
    }
    errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings {
        "400": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
        "500": iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateProblemDetailsFromDiscriminatorValue,
    }
    res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.CreateOdcsContractResultFromDiscriminatorValue, errorMapping)
    if err != nil {
        return nil, err
    }
    if res == nil {
        return nil, nil
    }
    return res.(iefa8953a3555be741841d5395d25b8cc91d8ea997e2cc98794b61191090ff773.OdcsContractResultable), nil
}
// ToGetRequestInformation returns ODCS contracts in the specified group with pagination.
// returns a *RequestInformation when successful
func (m *ItemContractsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemContractsRequestBuilderGetRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// ToPostRequestInformation submits an ODCS v3.1 YAML contract. The contract is parsed, stored as an ODCS_CONTRACT artifact, and its contents are projected onto the referenced schema artifact (labels, rules, field tags).
// returns a *RequestInformation when successful
func (m *ItemContractsRequestBuilder) ToPostRequestInformation(ctx context.Context, body []byte, requestConfiguration *ItemContractsRequestBuilderPostRequestConfiguration)(*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
    requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
    if requestConfiguration != nil {
        requestInfo.Headers.AddAll(requestConfiguration.Headers)
        requestInfo.AddRequestOptions(requestConfiguration.Options)
    }
    requestInfo.Headers.TryAdd("Accept", "application/json")
    requestInfo.SetStreamContentAndContentType(body, "application/x-yaml")
    return requestInfo, nil
}
// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *ItemContractsRequestBuilder when successful
func (m *ItemContractsRequestBuilder) WithUrl(rawUrl string)(*ItemContractsRequestBuilder) {
    return NewItemContractsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter);
}
