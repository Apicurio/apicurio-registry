package io.apicurio.registry.mcptools.rest.beans;

import java.util.List;

/**
 * Response envelope for the Compatible Tools endpoint.
 *
 * <p>Returns all registered MCP tools whose {@code inputSchema} can accept the output
 * produced by a given source tool's {@code outputSchema}.</p>
 */
public class McpCompatibleToolsResults {

    private long count;
    private List<McpToolSearchResult> tools;

    public McpCompatibleToolsResults() {
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public List<McpToolSearchResult> getTools() {
        return tools;
    }

    public void setTools(List<McpToolSearchResult> tools) {
        this.tools = tools;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final McpCompatibleToolsResults result = new McpCompatibleToolsResults();

        public Builder count(long count) {
            result.count = count;
            return this;
        }

        public Builder tools(List<McpToolSearchResult> tools) {
            result.tools = tools;
            return this;
        }

        public McpCompatibleToolsResults build() {
            return result;
        }
    }
}
