package wellknown

import (
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// AgentsWithGroupItemRequestBuilder builds and executes requests for operations under \well-known\agents\{groupId}
type AgentsWithGroupItemRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ByArtifactId get a specific registered agent card.
// returns a *AgentsItemWithArtifactItemRequestBuilder when successful
func (m *AgentsWithGroupItemRequestBuilder) ByArtifactId(artifactId string)(*AgentsItemWithArtifactItemRequestBuilder) {
    urlTplParams := make(map[string]string)
    for idx, item := range m.BaseRequestBuilder.PathParameters {
        urlTplParams[idx] = item
    }
    if artifactId != "" {
        urlTplParams["artifactId"] = artifactId
    }
    return NewAgentsItemWithArtifactItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}
// NewAgentsWithGroupItemRequestBuilderInternal instantiates a new AgentsWithGroupItemRequestBuilder and sets the default values.
func NewAgentsWithGroupItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*AgentsWithGroupItemRequestBuilder) {
    m := &AgentsWithGroupItemRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/agents/{groupId}", pathParameters),
    }
    return m
}
// NewAgentsWithGroupItemRequestBuilder instantiates a new AgentsWithGroupItemRequestBuilder and sets the default values.
func NewAgentsWithGroupItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*AgentsWithGroupItemRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewAgentsWithGroupItemRequestBuilderInternal(urlParams, requestAdapter)
}
