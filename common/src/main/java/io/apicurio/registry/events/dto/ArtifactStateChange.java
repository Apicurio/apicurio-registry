package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.quarkus.runtime.annotations.RegisterForReflection;


/**
 * Root Type for ArtifactStateChange
 * <p>
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "groupId",
    "artifactId",
    "state",
    "version"
})
@RegisterForReflection
public class ArtifactStateChange {

    @JsonProperty("groupId")
    private String groupId;

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("artifactId")
    private String artifactId;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("state")
    private String state;
    @JsonProperty("version")
    private String version;

    @JsonProperty("groupId")
    public String getGroupId() {
        return groupId;
    }

    @JsonProperty("groupId")
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("artifactId")
    public String getArtifactId() {
        return artifactId;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("artifactId")
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("state")
    public String getState() {
        return state;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("state")
    public void setState(String state) {
        this.state = state;
    }

    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

}
