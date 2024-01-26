package groups

import (
	"context"
	i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// ItemArtifactsItemBranchesWithBranchItemRequestBuilder manage a single artifact branch.
type ItemArtifactsItemBranchesWithBranchItemRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// ItemArtifactsItemBranchesWithBranchItemRequestBuilderDeleteRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemBranchesWithBranchItemRequestBuilderDeleteRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemArtifactsItemBranchesWithBranchItemRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemBranchesWithBranchItemRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemArtifactsItemBranchesWithBranchItemRequestBuilderPostRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemBranchesWithBranchItemRequestBuilderPostRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// ItemArtifactsItemBranchesWithBranchItemRequestBuilderPutRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type ItemArtifactsItemBranchesWithBranchItemRequestBuilderPutRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewItemArtifactsItemBranchesWithBranchItemRequestBuilderInternal instantiates a new WithBranchItemRequestBuilder and sets the default values.
func NewItemArtifactsItemBranchesWithBranchItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemBranchesWithBranchItemRequestBuilder {
	m := &ItemArtifactsItemBranchesWithBranchItemRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/groups/{groupId}/artifacts/{artifactId}/branches/{branchId}", pathParameters),
	}
	return m
}

// NewItemArtifactsItemBranchesWithBranchItemRequestBuilder instantiates a new WithBranchItemRequestBuilder and sets the default values.
func NewItemArtifactsItemBranchesWithBranchItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *ItemArtifactsItemBranchesWithBranchItemRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewItemArtifactsItemBranchesWithBranchItemRequestBuilderInternal(urlParams, requestAdapter)
}

// Delete deletes a single branch in the artifact. Any artifact versions that are not referenced by a branch are deleted as well, however, this does not happen until deletion of the "latest" branch is supported.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No artifact with this `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* Deletion of the "latest" branch is not supported (HTTP error `409`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemBranchesWithBranchItemRequestBuilder) Delete(ctx context.Context, requestConfiguration *ItemArtifactsItemBranchesWithBranchItemRequestBuilderDeleteRequestConfiguration) error {
	requestInfo, err := m.ToDeleteRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"409": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	err = m.BaseRequestBuilder.RequestAdapter.SendNoContent(ctx, requestInfo, errorMapping)
	if err != nil {
		return err
	}
	return nil
}

// Get returns a list of version identifiers in the artifact branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No artifact with this `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemBranchesWithBranchItemRequestBuilder) Get(ctx context.Context, requestConfiguration *ItemArtifactsItemBranchesWithBranchItemRequestBuilderGetRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactBranchFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable), nil
}

// Post add a new version to an artifact branch. Branch is created if it does not exist. Returns a list of version identifiers in the artifact branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No artifact with this `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* Version does not exist (HTTP error `409`)* Branch already contains given version. Artifact branches are append-only, cycles and history rewrites, except by replacing the entire branch using createOrReplaceArtifactBranch operation,  are not supported. (HTTP error `409`)* A server error occurred
func (m *ItemArtifactsItemBranchesWithBranchItemRequestBuilder) Post(ctx context.Context, body *string, requestConfiguration *ItemArtifactsItemBranchesWithBranchItemRequestBuilderPostRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable, error) {
	requestInfo, err := m.ToPostRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"409": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactBranchFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable), nil
}

// Put replace the sequence of versions contained in an artifact branch. Branch is created if it does not exist.  This operation is equivalent to deleting the artifact branch and adding each version in order to a new branch with the same name. This operation can be used to remove one or more versions from the branch. Returns a list of version identifiers in the artifact branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No artifact with this `artifactId` exists (HTTP error `404`)* Version does not exist (HTTP error `409`)* Request contains duplicate versions. Artifact branches are append-only, cycles and history rewrites, except by this operation, are not supported. (HTTP error `409`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemBranchesWithBranchItemRequestBuilder) Put(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable, requestConfiguration *ItemArtifactsItemBranchesWithBranchItemRequestBuilderPutRequestConfiguration) (i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable, error) {
	requestInfo, err := m.ToPutRequestInformation(ctx, body, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"400": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"404": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"409": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
		"500": i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.CreateArtifactBranchFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable), nil
}

// ToDeleteRequestInformation deletes a single branch in the artifact. Any artifact versions that are not referenced by a branch are deleted as well, however, this does not happen until deletion of the "latest" branch is supported.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No artifact with this `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* Deletion of the "latest" branch is not supported (HTTP error `409`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemBranchesWithBranchItemRequestBuilder) ToDeleteRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemBranchesWithBranchItemRequestBuilderDeleteRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.DELETE, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToGetRequestInformation returns a list of version identifiers in the artifact branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No artifact with this `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemBranchesWithBranchItemRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *ItemArtifactsItemBranchesWithBranchItemRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// ToPostRequestInformation add a new version to an artifact branch. Branch is created if it does not exist. Returns a list of version identifiers in the artifact branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No artifact with this `artifactId` exists (HTTP error `404`)* No branch with this `branchId` exists (HTTP error `404`)* Version does not exist (HTTP error `409`)* Branch already contains given version. Artifact branches are append-only, cycles and history rewrites, except by replacing the entire branch using createOrReplaceArtifactBranch operation,  are not supported. (HTTP error `409`)* A server error occurred
func (m *ItemArtifactsItemBranchesWithBranchItemRequestBuilder) ToPostRequestInformation(ctx context.Context, body *string, requestConfiguration *ItemArtifactsItemBranchesWithBranchItemRequestBuilderPostRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.POST, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	requestInfo.SetContentFromScalar(ctx, m.BaseRequestBuilder.RequestAdapter, "text/plain", body)
	return requestInfo, nil
}

// ToPutRequestInformation replace the sequence of versions contained in an artifact branch. Branch is created if it does not exist.  This operation is equivalent to deleting the artifact branch and adding each version in order to a new branch with the same name. This operation can be used to remove one or more versions from the branch. Returns a list of version identifiers in the artifact branch, ordered from the latest (tip of the branch) to the oldest.This operation can fail for the following reasons:* No group with this `groupId` exists (HTTP error `404`)* No artifact with this `artifactId` exists (HTTP error `404`)* Version does not exist (HTTP error `409`)* Request contains duplicate versions. Artifact branches are append-only, cycles and history rewrites, except by this operation, are not supported. (HTTP error `409`)* A server error occurred (HTTP error `500`)
func (m *ItemArtifactsItemBranchesWithBranchItemRequestBuilder) ToPutRequestInformation(ctx context.Context, body i00eb2e63d156923d00d8e86fe16b5d74daf30e363c9f185a8165cb42aa2f2c71.ArtifactBranchable, requestConfiguration *ItemArtifactsItemBranchesWithBranchItemRequestBuilderPutRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
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
func (m *ItemArtifactsItemBranchesWithBranchItemRequestBuilder) WithUrl(rawUrl string) *ItemArtifactsItemBranchesWithBranchItemRequestBuilder {
	return NewItemArtifactsItemBranchesWithBranchItemRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
