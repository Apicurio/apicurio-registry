/*
 * Copyright 2021 Red Hat
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
package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.quarkus.runtime.annotations.RegisterForReflection;


/**
 * Root Type for ArtifactId
 * <p>
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "groupId",
    "artifactId",
    "version",
    "type"
})
@RegisterForReflection
public class ArtifactId {

    @JsonProperty("groupId")
    private String groupId;

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("artifactId")
    private String artifactId;

    @JsonProperty("version")
    private String version;

    @JsonProperty("type")
    private String type;

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

    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

}
