package admin

import (
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// GitopsRequestBuilder builds and executes requests for operations under \admin\gitops
type GitopsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// NewGitopsRequestBuilderInternal instantiates a new GitopsRequestBuilder and sets the default values.
func NewGitopsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GitopsRequestBuilder {
	m := &GitopsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/admin/gitops", pathParameters),
	}
	return m
}

// NewGitopsRequestBuilder instantiates a new GitopsRequestBuilder and sets the default values.
func NewGitopsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *GitopsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewGitopsRequestBuilderInternal(urlParams, requestAdapter)
}

// Status get the current GitOps synchronization status.
// returns a *GitopsStatusRequestBuilder when successful
func (m *GitopsRequestBuilder) Status() *GitopsStatusRequestBuilder {
	return NewGitopsStatusRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}

// Sync trigger an immediate GitOps synchronization.
// returns a *GitopsSyncRequestBuilder when successful
func (m *GitopsRequestBuilder) Sync() *GitopsSyncRequestBuilder {
	return NewGitopsSyncRequestBuilderInternal(m.BaseRequestBuilder.PathParameters, m.BaseRequestBuilder.RequestAdapter)
}
