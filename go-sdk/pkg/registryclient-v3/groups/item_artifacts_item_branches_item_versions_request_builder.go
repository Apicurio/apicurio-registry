package groups

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemBranchesItemVersionsRequestBuilder manage the versions in a branch.
type ItemArtifactsItemBranchesItemVersionsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemBranchesItemVersionsRequestBuilderGetQueryParameters get a list of all versions in the branch.  Returns a list of version identifiers in the branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No artifact with this `groupId` and `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
type ItemArtifactsItemBranchesItemVersionsRequestBuilderGetQueryParameters struct {
	// The number of versions to return.  Defaults to 20.
	Limit *int32 `uriparametername:"limit"`
	// The number of versions to skip before starting to collect the result set.  Defaults to 0.
	Offset *int32 `uriparametername:"offset"`
}

// ItemArtifactsItemBranchesItemVersionsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemBranchesItemVersionsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
	// Request query parameters
	QueryParameters *ItemArtifactsItemBranchesItemVersionsRequestBuilderGetQueryParameters
}

// ItemArtifactsItemBranchesItemVersionsRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemBranchesItemVersionsRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemArtifactsItemBranchesItemVersionsRequestBuilderPutRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemBranchesItemVersionsRequestBuilderPutRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewItemArtifactsItemBranchesItemVersionsRequestBuilderInternal instantiates a new ItemArtifactsItemBranchesItemVersionsRequestBuilder and sets the default values.
func NewItemArtifactsItemBranchesItemVersionsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemBranchesItemVersionsRequestBuilder {
	m := &ItemArtifactsItemBranchesItemVersionsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/branches/{branchId}/versions{?limit*,offset*}", pathParameters),
	}
	return m
}

// NewItemArtifactsItemBranchesItemVersionsRequestBuilder instantiates a new ItemArtifactsItemBranchesItemVersionsRequestBuilder and sets the default values.
func NewItemArtifactsItemBranchesItemVersionsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemBranchesItemVersionsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemBranchesItemVersionsRequestBuilderInternal(urlParams, requestAdapter)
}

// Get get a list of all versions in the branch.  Returns a list of version identifiers in the branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No artifact with this `groupId` and `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a VersionSearchResultsable when successful
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemBranchesItemVersionsRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemBranchesItemVersionsRequestBuilderGetRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.VersionSearchResultsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateVersionSearchResultsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.VersionSearchResultsable), nil
}

// Post add a new version to an artifact branch. Returns a list of version identifiers in the branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No artifact with this `groupId` and `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* Branch already contains the given version. Artifact branches are append-only, cycles and history rewrites, except by replacing the entire branch using the replaceBranchVersions operation,  are not supported. (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 409 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemBranchesItemVersionsRequestBuilder) Post(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.AddVersionToBranchable, requestConfiguration *ItemArtifactsItemBranchesItemVersionsRequestBuilderPostRequestConfiguration) error {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
		"409": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// Put add a new version to an artifact branch. Branch is created if it does not exist. Returns a list of version identifiers in the artifact branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No artifact with this `groupId` and `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a ProblemDetails error when the service returns a 404 status code
// returns a ProblemDetails error when the service returns a 500 status code
func (m *ItemArtifactsItemBranchesItemVersionsRequestBuilder) Put(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ReplaceBranchVersionsable, requestConfiguration *ItemArtifactsItemBranchesItemVersionsRequestBuilderPutRequestConfiguration) error {
	requestInfo, err := m.ToPutRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateProblemDetailsFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// ToGetRequestInformation get a list of all versions in the branch.  Returns a list of version identifiers in the branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No artifact with this `groupId` and `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemBranchesItemVersionsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemBranchesItemVersionsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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

// ToPostRequestInformation add a new version to an artifact branch. Returns a list of version identifiers in the branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No artifact with this `groupId` and `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* Branch already contains the given version. Artifact branches are append-only, cycles and history rewrites, except by replacing the entire branch using the replaceBranchVersions operation,  are not supported. (HTTP error `409`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemBranchesItemVersionsRequestBuilder) ToPostRequestInformation(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.AddVersionToBranchable, requestConfiguration *ItemArtifactsItemBranchesItemVersionsRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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

// ToPutRequestInformation add a new version to an artifact branch. Branch is created if it does not exist. Returns a list of version identifiers in the artifact branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No artifact with this `groupId` and `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
// returns a *RequestInformation when successful
func (m *ItemArtifactsItemBranchesItemVersionsRequestBuilder) ToPutRequestInformation(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ReplaceBranchVersionsable, requestConfiguration *ItemArtifactsItemBranchesItemVersionsRequestBuilderPutRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
// returns a *ItemArtifactsItemBranchesItemVersionsRequestBuilder when successful
func (m *ItemArtifactsItemBranchesItemVersionsRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemBranchesItemVersionsRequestBuilder {
	return NewItemArtifactsItemBranchesItemVersionsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
