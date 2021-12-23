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

package io.apicurio.registry.rules.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.apicurio.registry.types.ArtifactType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author Fabian Martinez
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class WebhookExecuteRuleRequest {

    private String groupId;
    private String artifactId;

    private ArtifactType artifactType;

    private byte[] currentContentB64;
    private byte[] updatedContentB64;

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }
    /**
     * @param groupId the groupId to set
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }
    /**
     * @param artifactId the artifactId to set
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
    /**
     * @return the artifactType
     */
    public ArtifactType getArtifactType() {
        return artifactType;
    }
    /**
     * @param artifactType the artifactType to set
     */
    public void setArtifactType(ArtifactType artifactType) {
        this.artifactType = artifactType;
    }
    /**
     * @return the currentContentB64
     */
    public byte[] getCurrentContentB64() {
        return currentContentB64;
    }
    /**
     * @param currentContentB64 the currentContentB64 to set
     */
    public void setCurrentContentB64(byte[] currentContentB64) {
        this.currentContentB64 = currentContentB64;
    }
    /**
     * @return the updatedContentB64
     */
    public byte[] getUpdatedContentB64() {
        return updatedContentB64;
    }
    /**
     * @param updatedContentB64 the updatedContentB64 to set
     */
    public void setUpdatedContentB64(byte[] updatedContentB64) {
        this.updatedContentB64 = updatedContentB64;
    }

}
