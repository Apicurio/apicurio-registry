/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rest.v1.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.apicurio.registry.types.ArtifactState;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


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
    "labels",
    "createdBy",
    "createdOn",
    "modifiedBy",
    "modifiedOn",
    "id",
    "version",
    "type",
    "globalId",
    "state",
    "properties"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArtifactMetaData {

    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("labels")
    private List<String> labels;
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
    @JsonProperty("modifiedBy")
    private String modifiedBy;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("modifiedOn")
    private long modifiedOn;
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
     * (Required)
     * 
     */
    @JsonProperty("state")
    @JsonPropertyDescription("Describes the state of an artifact or artifact version.  The following states\nare possible:\n\n* ENABLED\n* DISABLED\n* DEPRECATED\n")
    private ArtifactState state;

    @JsonProperty("properties")
    @JsonPropertyDescription("A set of name-value properties for an artifact or artifact version.")
    private Map<String, String> properties;


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
    public long getModifiedOn() {
        return modifiedOn;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("modifiedOn")
    public void setModifiedOn(long modifiedOn) {
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
        return "ArtifactMetaData{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", labels='" + (labels == null ? "[]" : labels.toArray().toString()) + '\'' +
                ", createdBy='" + createdBy + '\'' +
                ", createdOn=" + createdOn +
                ", modifiedBy='" + modifiedBy + '\'' +
                ", modifiedOn=" + modifiedOn +
                ", id='" + id + '\'' +
                ", version=" + version +
                ", type=" + type +
                ", globalId=" + globalId +
                ", state=" + state +
                ", properties=" + (properties == null ? "{}" : "{" + properties.entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining(", ")) + "}") +
                '}';
    }
}
