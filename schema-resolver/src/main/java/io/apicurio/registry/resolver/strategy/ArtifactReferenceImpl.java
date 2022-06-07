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

package io.apicurio.registry.resolver.strategy;

import io.apicurio.registry.resolver.SchemaResolverConfig;

/**
 * @see ArtifactReference
 *
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class ArtifactReferenceImpl implements ArtifactReference {

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
     * Optional, unless the rest of the fields are empty or {@link SchemaResolverConfig#USE_ID} is configured with IdOption.contentId
     */
    private Long contentId;

    protected ArtifactReferenceImpl() {
        //empty initialize using setters
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReference#hasValue()
     */
    @Override
    public boolean hasValue() {
        return groupId != null || artifactId != null || version != null || globalId != null || contentId != null;
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReference#getGroupId()
     */
    @Override
    public String getGroupId() {
        return groupId;
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReference#getArtifactId()
     */
    @Override
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReference#getVersion()
     */
    @Override
    public String getVersion() {
        return version;
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReference#getGlobalId()
     */
    @Override
    public Long getGlobalId() {
        return globalId;
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReference#getContentId()
     */
    @Override
    public Long getContentId() {
        return contentId;
    }

    /**
     * @param groupId the groupId to set
     */
    protected void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * @param artifactId the artifactId to set
     */
    protected void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @param version the version to set
     */
    protected void setVersion(String version) {
        this.version = version;
    }

    /**
     * @param globalId the globalId to set
     */
    protected void setGlobalId(Long globalId) {
        this.globalId = globalId;
    }

    /**
     * @param contentId the contentId to set
     */
    protected void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReference#hashCode()
     */
    @Override
    public int hashCode() {
        return globalId == null ? 0 : globalId.hashCode();
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReference#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ArtifactReferenceImpl other = (ArtifactReferenceImpl) obj;

        boolean match1 = false;
        if(globalId != null  && other.globalId != null) {
            if (!globalId.equals(other.globalId)) {
                return false;
            } else {
                match1 = true;
            }
        }

        boolean match2 = false;
        if(contentId != null  && other.contentId != null) {
            if (!contentId.equals(other.contentId)) {
                return false;
            } else {
                match2 = true;
            }
        }

        boolean match3 = false;
        if(groupId != null  && other.groupId != null) {
            if (!groupId.equals(other.groupId)) {
                return false;
            } else {
                match3 = true;
            }
        }

        boolean match4 = false;
        if(artifactId != null  && other.artifactId != null) {
            if (!artifactId.equals(other.artifactId)) {
                return false;
            } else {
                match4 = true;
            }
        }

        boolean match5 = false;
        if(version != null  && other.version != null) {
            if (!version.equals(other.version)) {
                return false;
            } else {
                match5 = true;
            }
        }

        return match1 || match2 || (match3 && match4 && match5);
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReference#toString()
     */
    @Override
    public String toString() {
        return "ArtifactReference [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version
                + ", globalId=" + globalId + ", contentId=" + contentId + "]";
    }

    public static class ArtifactReferenceBuilder {

        private ArtifactReferenceImpl reference;

        public ArtifactReferenceBuilder() {
            reference = new ArtifactReferenceImpl();
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

        public ArtifactReferenceImpl build() {
            return reference;
        }

    }
}
