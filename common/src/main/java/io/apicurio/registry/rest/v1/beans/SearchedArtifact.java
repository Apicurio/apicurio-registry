/*
 * Copyright 2020 Red Hat
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
    "id",
    "name",
    "description",
    "createdOn",
    "createdBy",
    "type",
    "labels",
    "state",
    "modifiedOn",
    "modifiedBy"
})
public class SearchedArtifact {

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
     */
    @JsonProperty("modifiedOn")
    @JsonPropertyDescription("")
    private long modifiedOn;
    /**
     *
     */
    @JsonProperty("modifiedBy")
    @JsonPropertyDescription("")
    private String modifiedBy;

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
     */
    @JsonProperty("modifiedOn")
    public long getModifiedOn() {
        return modifiedOn;
    }

    /**
     *
     */
    @JsonProperty("modifiedOn")
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    /**
     *
     */
    @JsonProperty("modifiedBy")
    public String getModifiedBy() {
        return modifiedBy;
    }

    /**
     * 
     */
    @JsonProperty("modifiedBy")
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    @Override
    public String toString() {
        return "SearchedArtifact{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", createdOn=" + createdOn +
                ", createdBy='" + createdBy + '\'' +
                ", type=" + type +
                ", labels=" + labels +
                ", state=" + state +
                ", modifiedOn=" + modifiedOn +
                ", modifiedBy='" + modifiedBy + '\'' +
                '}';
    }
}
