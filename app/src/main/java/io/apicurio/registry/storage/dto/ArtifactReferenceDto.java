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

package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class ArtifactReferenceDto {

    private String tenantId;
    private String groupId;
    private String artifactId;
    private String version;
    private int versionId;
    private String name;

    /**
     * Constructor
     */
    public ArtifactReferenceDto() {
    }

    /**
     * @return the tenantId
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * @param tenantId to be set
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @param groupId to be set
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
     * @param artifactId to be set
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version to be set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return versionId
     */
    public int getVersionId() {
        return versionId;
    }

    /**
     * @param versionId to be set
     */
    public void setVersionId(int versionId) {
        this.versionId = versionId;
    }

    /**
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name to be set
     */
    public void setName(String name) {
        this.name = name;
    }
}
