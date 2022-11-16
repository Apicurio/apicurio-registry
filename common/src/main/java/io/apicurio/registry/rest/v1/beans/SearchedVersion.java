
package io.apicurio.registry.rest.v1.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.apicurio.registry.types.ArtifactState;

import java.util.ArrayList;
import java.util.List;


/**
 * Models a single artifact from the result set returned when searching for artifacts.
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "description",
    "createdOn",
    "createdBy",
    "type",
    "labels",
    "state",
    "globalId",
    "version"
})
public class SearchedVersion {

    /**
     *
     */
    @JsonProperty("name")
    @JsonPropertyDescription("")
    private String name;
    /**
     *
     */
    @JsonProperty("description")
    @JsonPropertyDescription("")
    private String description;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("createdOn")
    @JsonPropertyDescription("")
    private long createdOn;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("createdBy")
    @JsonPropertyDescription("")
    private String createdBy;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("type")
    @JsonPropertyDescription("")
    private String type;
    /**
     *
     */
    @JsonProperty("labels")
    @JsonPropertyDescription("")
    private List<String> labels = new ArrayList<String>();
    /**
     * Describes the state of an artifact or artifact version.  The following states
     * are possible:
     *
     * * ENABLED
     * * DISABLED
     * * DEPRECATED
     *
     * (Required)
     *
     */
    @JsonProperty("state")
    @JsonPropertyDescription("Describes the state of an artifact or artifact version.  The following states\nare possible:\n\n* ENABLED\n* DISABLED\n* DEPRECATED\n")
    private ArtifactState state;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("globalId")
    @JsonPropertyDescription("")
    private Long globalId;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("version")
    @JsonPropertyDescription("")
    private Long version;

    /**
     *
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     *
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     *
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("createdOn")
    public long getCreatedOn() {
        return createdOn;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("createdOn")
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("createdBy")
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("createdBy")
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    /**
     *
     */
    @JsonProperty("labels")
    public List<String> getLabels() {
        return labels;
    }

    /**
     *
     */
    @JsonProperty("labels")
    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    /**
     * Describes the state of an artifact or artifact version.  The following states
     * are possible:
     *
     * * ENABLED
     * * DISABLED
     * * DEPRECATED
     *
     * (Required)
     *
     */
    @JsonProperty("state")
    public ArtifactState getState() {
        return state;
    }

    /**
     * Describes the state of an artifact or artifact version.  The following states
     * are possible:
     *
     * * ENABLED
     * * DISABLED
     * * DEPRECATED
     *
     * (Required)
     *
     */
    @JsonProperty("state")
    public void setState(ArtifactState state) {
        this.state = state;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("globalId")
    public Long getGlobalId() {
        return globalId;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("globalId")
    public void setGlobalId(Long globalId) {
        this.globalId = globalId;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("version")
    public Long getVersion() {
        return version;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("version")
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "SearchedVersion{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", createdOn=" + createdOn +
                ", createdBy='" + createdBy + '\'' +
                ", type=" + type +
                ", labels=" + labels +
                ", state=" + state +
                ", globalId=" + globalId +
                ", version=" + version +
                '}';
    }
}
