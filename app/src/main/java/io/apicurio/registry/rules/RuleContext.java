/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.rules;

import java.util.Objects;

import io.apicurio.registry.types.ArtifactType;

/**
 * Contains all of the information needed by a rule executor, including the rule-specific
 * configuration, current & updated content, and any other meta-data needed.
 * @author Ales Justin
 */
public class RuleContext {
    private final String artifactId;
    private final ArtifactType artifactType;
    private final String configuration;
    private final String currentContent;
    private final String updatedContent;

    /**
     * Constructor.
     * @param artifactId
     * @param artifactType
     * @param configuration
     * @param currentContent
     * @param updatedContent
     */
    public RuleContext(String artifactId, ArtifactType artifactType, String configuration,
            String currentContent, String updatedContent) {
        this.artifactId = Objects.requireNonNull(artifactId);
        this.artifactType = Objects.requireNonNull(artifactType);
        this.configuration = Objects.requireNonNull(configuration);
        this.currentContent = currentContent; // Current Content will be null when creating an artifact.
        this.updatedContent = Objects.requireNonNull(updatedContent);
    }
    
    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @return the artifactType
     */
    public ArtifactType getArtifactType() {
        return artifactType;
    }

    /**
     * @return the configuration
     */
    public String getConfiguration() {
        return configuration;
    }

    /**
     * @return the currentContent
     */
    public String getCurrentContent() {
        return currentContent;
    }
    
    /**
     * @return the updatedContent
     */
    public String getUpdatedContent() {
        return updatedContent;
    }
}
