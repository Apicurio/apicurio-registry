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

package io.apicurio.registry.storage.impl;

import java.io.Serializable;
import java.util.Objects;

/**
 * Models a unique key for an artifact.  This includes the GroupId and ArtifactId of the
 * artifact.
 * @author eric.wittmann@gmail.com
 */
public class ArtifactKey implements Serializable {
    
    private static final long serialVersionUID = -3615944375480633222L;
    
    private String groupId;
    private String artifactId;
    
    /**
     * Constructor.
     * @param groupId
     * @param artifactId
     */
    public ArtifactKey(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }
    
    /**
     * Constructor.
     */
    public ArtifactKey() {
    }
    
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
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(artifactId, groupId);
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
        ArtifactKey other = (ArtifactKey) obj;
        return Objects.equals(artifactId, other.artifactId) && Objects.equals(groupId, other.groupId);
    }
    
    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("ArtifactKey(%s, %s)", this.groupId, this.artifactId);
    }

}
