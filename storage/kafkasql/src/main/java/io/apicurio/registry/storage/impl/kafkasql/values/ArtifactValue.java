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

package io.apicurio.registry.storage.impl.kafkasql.values;

import java.util.Date;

import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.types.ArtifactState;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
@ToString
public class ArtifactValue extends ArtifactVersionValue {

    private Long globalId;
    private String version;
    private String artifactType;
    private String contentHash;
    private String createdBy;
    private Date createdOn;
    private Integer versionId;
    private Long contentId;
    private Boolean latest;

    /**
     * Creator method.
     */
    public static final ArtifactValue create(ActionType action, Long globalId, String version, String artifactType, String contentHash,
            String createdBy, Date createdOn, EditableArtifactMetaDataDto metaData, Integer versionId, ArtifactState state, Long contentId,
            Boolean latest) {
        ArtifactValue value = new ArtifactValue();
        value.setAction(action);
        value.setGlobalId(globalId);
        value.setVersion(version);
        value.setArtifactType(artifactType);
        value.setContentHash(contentHash);
        value.setCreatedBy(createdBy);
        value.setCreatedOn(createdOn);
        value.setMetaData(metaData);
        value.setVersionId(versionId);
        value.setState(state);
        value.setContentId(contentId);
        value.setLatest(latest);
        return value;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.values.MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Artifact;
    }

    /**
     * @return the artifactType
     */
    public String getArtifactType() {
        return artifactType;
    }

    /**
     * @param artifactType the artifactType to set
     */
    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    /**
     * @return the contentHash
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * @param contentHash the contentHash to set
     */
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    /**
     * @return the createdBy
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * @param createdBy the createdBy to set
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * @return the createdOn
     */
    public Date getCreatedOn() {
        return createdOn;
    }

    /**
     * @param createdOn the createdOn to set
     */
    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return the globalId
     */
    public Long getGlobalId() {
        return globalId;
    }

    /**
     * @param globalId the globalId to set
     */
    public void setGlobalId(Long globalId) {
        this.globalId = globalId;
    }

    /**
     * @return the versionId
     */
    public Integer getVersionId() {
        return versionId;
    }

    /**
     * @param versionId the versionId to set
     */
    public void setVersionId(Integer versionId) {
        this.versionId = versionId;
    }

    /**
     * @return the contentId
     */
    public Long getContentId() {
        return contentId;
    }

    /**
     * @param contentId the contentId to set
     */
    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    /**
     * @return the latest
     */
    public Boolean getLatest() {
        return latest;
    }

    /**
     * @param latest the latest to set
     */
    public void setLatest(Boolean latest) {
        this.latest = latest;
    }

}
