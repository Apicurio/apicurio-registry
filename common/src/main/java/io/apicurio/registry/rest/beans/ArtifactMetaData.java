
package io.apicurio.registry.rest.beans;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import io.apicurio.registry.types.ArtifactType;


/**
 * Root Type for ArtifactMetaData
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "description",
    "createdBy",
    "createdOn",
    "modifiedBy",
    "modifiedOn",
    "id",
    "version",
    "type",
    "globalId",
    "state"
})
public class ArtifactMetaData {

    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
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
    private Date createdOn;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedBy")
    private String modifiedBy;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedOn")
    private Date modifiedOn;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("id")
    @JsonPropertyDescription("")
    private String id;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("")
    private Integer version;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("")
    private ArtifactType type;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("globalId")
    @JsonPropertyDescription("")
    private Integer globalId;
    /**
     * 
     * 
     */
    @JsonProperty("state")
    @JsonPropertyDescription("")
    private ArtifactMetaData.State state;

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
    public Date getCreatedOn() {
        return createdOn;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("createdOn")
    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedBy")
    public String getModifiedBy() {
        return modifiedBy;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedBy")
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedOn")
    public Date getModifiedOn() {
        return modifiedOn;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("modifiedOn")
    public void setModifiedOn(Date modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

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

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("type")
    public ArtifactType getType() {
        return type;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("type")
    public void setType(ArtifactType type) {
        this.type = type;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("globalId")
    public Integer getGlobalId() {
        return globalId;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("globalId")
    public void setGlobalId(Integer globalId) {
        this.globalId = globalId;
    }

    /**
     * 
     * 
     */
    @JsonProperty("state")
    public ArtifactMetaData.State getState() {
        return state;
    }

    /**
     * 
     * 
     */
    @JsonProperty("state")
    public void setState(ArtifactMetaData.State state) {
        this.state = state;
    }

    public enum State {

        ENABLED("ENABLED"),
        DISABLED("DISABLED"),
        DEPRECATED("DEPRECATED"),
        DELETED("DELETED");
        private final String value;
        private final static Map<String, ArtifactMetaData.State> CONSTANTS = new HashMap<String, ArtifactMetaData.State>();

        static {
            for (ArtifactMetaData.State c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private State(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static ArtifactMetaData.State fromValue(String value) {
            ArtifactMetaData.State constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
