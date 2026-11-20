package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

/**
 * Event data payload for {@link CloudEventType#ARTIFACT_VERSION_PUBLISHED}. The inherited
 * {@link ArtifactId#getVersion()} identifies the published version.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "groupId", "artifactId", "version", "type", "name", "description", "createdOn" })
@RegisterForReflection
public class VersionMetadata extends ArtifactId {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("createdOn")
    private Instant createdOn;

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

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Instant createdOn) {
        this.createdOn = createdOn;
    }
}
