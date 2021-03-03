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

package io.apicurio.registry.maven;

import java.io.File;

import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author eric.wittmann@gmail.com
 */
public class RegisterArtifact {

    private String groupId;
    private String artifactId;
    private String version;
    private ArtifactType type;
    private File file;
    private IfExists ifExists;
    private Boolean canonicalize;

    /**
     * Constructor.
     */
    public RegisterArtifact() {
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
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * @param file the file to set
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * @return the ifExists
     */
    public IfExists getIfExists() {
        return ifExists;
    }

    /**
     * @param ifExists the ifExists to set
     */
    public void setIfExists(IfExists ifExists) {
        this.ifExists = ifExists;
    }

    /**
     * @return the canonicalize
     */
    public Boolean getCanonicalize() {
        return canonicalize;
    }

    /**
     * @param canonicalize the canonicalize to set
     */
    public void setCanonicalize(Boolean canonicalize) {
        this.canonicalize = canonicalize;
    }

    /**
     * @return the type
     */
    public ArtifactType getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(ArtifactType type) {
        this.type = type;
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

}
