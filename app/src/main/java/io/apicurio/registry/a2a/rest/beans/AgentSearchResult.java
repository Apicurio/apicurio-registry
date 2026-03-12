package io.apicurio.registry.a2a.rest.beans;

import java.util.List;

/**
 * A single agent card search result with metadata.
 */
public class AgentSearchResult {

    private String groupId;
    private String artifactId;
    private String name;
    private String description;
    private String version;
    private String url;
    private List<String> skills;
    private AgentCapabilities capabilities;
    private long createdOn;
    private String owner;

    public AgentSearchResult() {
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public AgentCapabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(AgentCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public long getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AgentSearchResult result = new AgentSearchResult();

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

        public Builder description(String description) {
            result.description = description;
            return this;
        }

        public Builder version(String version) {
            result.version = version;
            return this;
        }

        public Builder url(String url) {
            result.url = url;
            return this;
        }

        public Builder skills(List<String> skills) {
            result.skills = skills;
            return this;
        }

        public Builder capabilities(AgentCapabilities capabilities) {
            result.capabilities = capabilities;
            return this;
        }

        public Builder createdOn(long createdOn) {
            result.createdOn = createdOn;
            return this;
        }

        public Builder owner(String owner) {
            result.owner = owner;
            return this;
        }

        public AgentSearchResult build() {
            return result;
        }
    }
}
