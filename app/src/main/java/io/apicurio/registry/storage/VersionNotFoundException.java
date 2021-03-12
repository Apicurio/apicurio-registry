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
public class VersionNotFoundException extends ArtifactNotFoundException {

    private static final long serialVersionUID = 969959730600115392L;

    private final String version;

    /**
     * Constructor.
     */
    public VersionNotFoundException(String groupId, String artifactId, String version) {
        super(groupId, artifactId);
        this.version = version;
    }

    public VersionNotFoundException(String groupId, String artifactId, String version, Throwable cause) {
        super(groupId, artifactId, cause);
        this.version = version;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "No version '" + this.version + "' found for artifact with ID '" + this.getArtifactId() + "' in group '" + getGroupId() + "'.";
    }

}
