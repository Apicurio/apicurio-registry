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

    private ArtifactReference(String groupId, String artifactId, String version, Long globalId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.globalId = globalId;
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
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
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
                + ", globalId=" + globalId + "]";
    }

    public static ArtifactReferenceBuilder builder(){
        return new ArtifactReferenceBuilder();
    }

    public static class ArtifactReferenceBuilder {

        private String groupId;
        private String artifactId;
        private String version;
        private Long globalId;

        ArtifactReferenceBuilder() {
            //empty
        }

        public ArtifactReferenceBuilder groupId(String groupId) {
            this.groupId = groupId;
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder version(String version) {
            this.version = version;
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder globalId(Long globalId) {
            this.globalId = globalId;
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
            return new ArtifactReference(this.groupId, this.artifactId, this.version, this.globalId);
        }

    }
}
