
package io.apicurio.registry.rest.v1.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.apicurio.registry.types.ArtifactState;

import java.util.List;
import java.util.Map;


/**
 * Root Type for ArtifactVersionMetaData
 * <p>
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "version",
    "name",
    "description",
    "labels",
    "createdBy",
    "createdOn",
    "type",
    "globalId",
    "state",
    "id",
    "properties"
})
public class VersionMetaData {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    private Integer version;
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("labels")
    private List<String> labels;
    @JsonProperty("properties")
    private Map<String, String> properties;

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("createdBy")
    private String createdBy;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("createdOn")
    private long createdOn;
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
     * (Required)
     *
     */
    @JsonProperty("globalId")
    @JsonPropertyDescription("")
    private Long globalId;
    /**
     * Describes the state of an artifact or artifact version.  The following states
     * are possible:
     * 
     * * ENABLED
     * * DISABLED
     * * DEPRECATED
     * 
     * 
     */
    @JsonProperty("state")
    @JsonPropertyDescription("Describes the state of an artifact or artifact version.  The following states\nare possible:\n\n* ENABLED\n* DISABLED\n* DEPRECATED\n")
    private ArtifactState state;
    /**
     * The artifact id.
     * (Required)
     * 
     */
    @JsonProperty("id")
    @JsonPropertyDescription("The artifact id.")
    private String id;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public Integer getVersion() {
        return version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
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
     * Describes the state of an artifact or artifact version.  The following states
     * are possible:
     * 
     * * ENABLED
     * * DISABLED
     * * DEPRECATED
     * 
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
     * 
     */
    @JsonProperty("state")
    public void setState(ArtifactState state) {
        this.state = state;
    }

    /**
     * The artifact id.
     * (Required)
     * 
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * The artifact id.
     * (Required)
     * 
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("labels")
    public List<String> getLabels() {
        return labels;
    }

    @JsonProperty("labels")
    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    @JsonProperty("properties")
    public Map<String, String> getProperties() {
        return properties;
    }

    @JsonProperty("properties")
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "VersionMetaData{" +
                "version=" + version +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", labels=" + labels +
                ", properties=" + properties +
                ", createdBy='" + createdBy + '\'' +
                ", createdOn=" + createdOn +
                ", type=" + type +
                ", globalId=" + globalId +
                ", state=" + state +
                ", id='" + id + '\'' +
                '}';
    }
}
