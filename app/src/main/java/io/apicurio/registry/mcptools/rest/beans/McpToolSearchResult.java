package io.apicurio.registry.mcptools.rest.beans;

import java.util.List;

/**
 * A single MCP tool search result with metadata.
 */
public class McpToolSearchResult {

    private String groupId;
    private String artifactId;
    private String name;
    private String title;
    private String description;
    private String owner;
    private long createdOn;
    private List<String> parameters;

    public McpToolSearchResult() {
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public long getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final McpToolSearchResult result = new McpToolSearchResult();

        public Builder groupId(String groupId) {
            result.groupId = groupId;
            return this;
        }

        public Builder artifactId(String artifactId) {
            result.artifactId = artifactId;
            return this;
        }

        public Builder name(String name) {
            result.name = name;
            return this;
        }

        public Builder title(String title) {
            result.title = title;
            return this;
        }

        public Builder description(String description) {
            result.description = description;
            return this;
        }

        public Builder owner(String owner) {
            result.owner = owner;
            return this;
        }

        public Builder createdOn(long createdOn) {
            result.createdOn = createdOn;
            return this;
        }

        public Builder parameters(List<String> parameters) {
            result.parameters = parameters;
            return this;
        }

        public McpToolSearchResult build() {
            return result;
        }
    }
}
