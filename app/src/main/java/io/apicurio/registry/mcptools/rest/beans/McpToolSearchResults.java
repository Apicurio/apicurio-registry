package io.apicurio.registry.mcptools.rest.beans;

import java.util.List;

/**
 * Search results for MCP tool definitions.
 */
public class McpToolSearchResults {

    private long count;
    private List<McpToolSearchResult> tools;

    public McpToolSearchResults() {
    }

    public McpToolSearchResults(long count, List<McpToolSearchResult> tools) {
        this.count = count;
        this.tools = tools;
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
        private long count;
        private List<McpToolSearchResult> tools;

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public Builder tools(List<McpToolSearchResult> tools) {
            this.tools = tools;
            return this;
        }

        public McpToolSearchResults build() {
            return new McpToolSearchResults(count, tools);
        }
    }
}
