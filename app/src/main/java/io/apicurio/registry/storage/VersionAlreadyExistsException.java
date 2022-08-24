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
 * @author Miroslav Safar (msafar@redhat.com)
 */
public class VersionAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = 3567623491368394677L;

    private final String groupId;
    private final String artifactId;
    private final String version;

    /**
     * Constructor.
     *
     * @param groupId
     * @param artifactId
     * @param version
     */
    public VersionAlreadyExistsException(String groupId, String artifactId, String version) {
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.version = version;
    }

    /**
     * @return the artifactId
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
     * @return the version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * @see Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "An artifact version '" + this.version + "' for artifact ID '" + this.artifactId + "' in group '" + groupId + "' already exists.";
    }

}
