/*
 * Copyright 2022 Red Hat
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

/**
 * @author Carles Arnal
 * @see ArtifactReference
 */
public class ArtifactCoordinates {


    private String groupId;

    private String artifactId;

    private String version;

    protected ArtifactCoordinates() {
        //empty initialize using setters
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
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

    @Override
    public int hashCode() {
        int result = groupId != null ? groupId.hashCode() : 0;
        result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
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
        ArtifactCoordinates other = (ArtifactCoordinates) obj;

        if (groupId != null && other.groupId != null) {
            if (!groupId.equals(other.groupId)) {
                return false;
            }
        }

        if (artifactId != null && other.artifactId != null) {
            if (!artifactId.equals(other.artifactId)) {
                return false;
            }
        }

        if (version != null && other.version != null) {
            return version.equals(other.version);
        }

        return true;
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactCoordinates#toString()
     */
    @Override
    public String toString() {
        return "ArtifactCoordinates [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + "]";
    }

    public static ArtifactCoordinates fromArtifactReference(ArtifactReference artifactReference) {
        return builder().artifactId(artifactReference.getArtifactId())
                .groupId(artifactReference.getGroupId())
                .version(artifactReference.getVersion())
                .build();
    }

    public static ArtifactCoordinatesBuilder builder() {
        return new ArtifactCoordinatesBuilder();
    }

    public static class ArtifactCoordinatesBuilder {

        private ArtifactCoordinates coordinates;

        public ArtifactCoordinatesBuilder() {
            coordinates = new ArtifactCoordinates();
        }

        public ArtifactCoordinatesBuilder groupId(String groupId) {
            coordinates.setGroupId(groupId);
            return ArtifactCoordinatesBuilder.this;
        }

        public ArtifactCoordinatesBuilder artifactId(String artifactId) {
            coordinates.setArtifactId(artifactId);
            return ArtifactCoordinatesBuilder.this;
        }

        public ArtifactCoordinatesBuilder version(String version) {
            coordinates.setVersion(version);
            return ArtifactCoordinatesBuilder.this;
        }

        public ArtifactCoordinates build() {
            return coordinates;
        }
    }
}