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

package io.apicurio.registry.serde.strategy;

import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.config.IdOption;

/**
 * This class holds the information that reference one Artifact in Apicurio Registry. It will always make
 * reference to an artifact in a group. Optionally it can reference to a specific version.
 *
 * @author Fabian Martinez
 */
public class ArtifactReference {

    /**
     * Optional, unless globalId is empty
     */
    private String groupId;
    /**
     * Optional, unless globalId is empty
     */
    private String artifactId;
    /**
     * Optional
     */
    private String version;

    /**
     * Optional, unless the rest of the fields are empty
     */
    private Long globalId;

    /**
     * Optional, unless the rest of the fields are empty or {@link SerdeConfig#USE_ID} is configured with {@link IdOption#contentId}
     */
    private Long contentId;

    private ArtifactReference() {
        //empty initialize using setters
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return the globalId
     */
    public Long getGlobalId() {
        return globalId;
    }

    /**
     * @return the contentId
     */
    public Long getContentId() {
        return contentId;
    }

    /**
     * @param groupId the groupId to set
     */
    private void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * @param artifactId the artifactId to set
     */
    private void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @param version the version to set
     */
    private void setVersion(String version) {
        this.version = version;
    }

    /**
     * @param globalId the globalId to set
     */
    private void setGlobalId(Long globalId) {
        this.globalId = globalId;
    }

    /**
     * @param contentId the contentId to set
     */
    private void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((contentId == null) ? 0 : contentId.hashCode());
        result = prime * result + ((globalId == null) ? 0 : globalId.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ArtifactReference other = (ArtifactReference) obj;
        if (artifactId == null) {
            if (other.artifactId != null)
                return false;
        } else if (!artifactId.equals(other.artifactId))
            return false;
        if (contentId == null) {
            if (other.contentId != null)
                return false;
        } else if (!contentId.equals(other.contentId))
            return false;
        if (globalId == null) {
            if (other.globalId != null)
                return false;
        } else if (!globalId.equals(other.globalId))
            return false;
        if (groupId == null) {
            if (other.groupId != null)
                return false;
        } else if (!groupId.equals(other.groupId))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ArtifactReference [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version
                + ", globalId=" + globalId + ", contentId=" + contentId + "]";
    }

    public static ArtifactReference fromGlobalId(Long globalId) {
        return builder().globalId(globalId).build();
    }

    public static ArtifactReferenceBuilder builder(){
        return new ArtifactReferenceBuilder();
    }

    public static class ArtifactReferenceBuilder {

        private ArtifactReference reference;

        ArtifactReferenceBuilder() {
            reference = new ArtifactReference();
        }

        public ArtifactReferenceBuilder groupId(String groupId) {
            reference.setGroupId(groupId);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder artifactId(String artifactId) {
            reference.setArtifactId(artifactId);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder version(String version) {
            reference.setVersion(version);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder globalId(Long globalId) {
            reference.setGlobalId(globalId);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder contentId(Long contentId) {
            reference.setContentId(contentId);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReference build() {
            //TODO revisit, not sure if this is a good idea
//            if (groupId == null && artifactId == null) {
//                Objects.requireNonNull(globalId, "globalId is required if no groupId and artifactId is provided");
//            }
//            if (globalId == null) {
//                Objects.requireNonNull(groupId, "groupId is required");
//            }
//            if (globalId == null) {
//                Objects.requireNonNull(artifactId, "artifactId is required");
//            }
            return reference;
        }

    }
}
