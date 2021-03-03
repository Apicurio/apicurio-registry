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

package io.apicurio.registry.storage;

/**
 * @author eric.wittmann@gmail.com
 */
public class ArtifactNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -3614783501078800654L;

    private String groupId;
    private String artifactId;

    /**
     * Constructor.
     * @param groupId
     * @param artifactId
     */
    public ArtifactNotFoundException(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    /**
     * Constructor.
     * @param groupId
     * @param artifactId
     * @param cause
     */
    public ArtifactNotFoundException(String groupId, String artifactId, Throwable cause) {
        super("Artifact with ID '" + artifactId + "' in group '" + groupId + "'  not found.", cause);
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public ArtifactNotFoundException(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return the artifactId
     *
     * This value is informative.
     * MAY be null.
     */
    public String getArtifactId() {
        return artifactId;
    }
    
    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "No artifact with ID '" + this.artifactId + "' in group '" + this.groupId + "' was found.";
    }

}
