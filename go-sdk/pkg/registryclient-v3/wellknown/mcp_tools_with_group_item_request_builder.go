package wellknown

import (
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// McpToolsWithGroupItemRequestBuilder builds and executes requests for operations under \well-known\mcp-tools\{groupId}
type McpToolsWithGroupItemRequestBuilder struct {
    i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}
// ByArtifactId get a specific registered MCP tool definition.
// returns a *McpToolsItemWithArtifactItemRequestBuilder when successful
func (m *McpToolsWithGroupItemRequestBuilder) ByArtifactId(artifactId string)(*McpToolsItemWithArtifactItemRequestBuilder) {
    urlTplParams := make(map[string]string)
    for idx, item := range m.BaseRequestBuilder.PathParameters {
        urlTplParams[idx] = item
    }
    if artifactId != "" {
        urlTplParams["artifactId"] = artifactId
    }
    return NewMcpToolsItemWithArtifactItemRequestBuilderInternal(urlTplParams, m.BaseRequestBuilder.RequestAdapter)
}
// NewMcpToolsWithGroupItemRequestBuilderInternal instantiates a new McpToolsWithGroupItemRequestBuilder and sets the default values.
func NewMcpToolsWithGroupItemRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*McpToolsWithGroupItemRequestBuilder) {
    m := &McpToolsWithGroupItemRequestBuilder{
        BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/well-known/mcp-tools/{groupId}", pathParameters),
    }
    return m
}
// NewMcpToolsWithGroupItemRequestBuilder instantiates a new McpToolsWithGroupItemRequestBuilder and sets the default values.
func NewMcpToolsWithGroupItemRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter)(*McpToolsWithGroupItemRequestBuilder) {
    urlParams := make(map[string]string)
    urlParams["request-raw-url"] = rawUrl
    return NewMcpToolsWithGroupItemRequestBuilderInternal(urlParams, requestAdapter)
}
