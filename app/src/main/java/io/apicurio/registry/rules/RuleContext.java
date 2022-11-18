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

package io.apicurio.registry.rules;

import io.apicurio.registry.content.ContentHandle;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contains all of the information needed by a rule executor, including the rule-specific
 * configuration, current and updated content, and any other meta-data needed.
 *
 * @author Ales Justin
 */
public class RuleContext {
    private final String groupId;
    private final String artifactId;
    private final String artifactType;
    private final String configuration;
    private final List<ContentHandle> currentContent;
    private final ContentHandle updatedContent;
    private final Map<String, ContentHandle> resolvedReferences;

    /**
     * Constructor.
     *
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param configuration
     * @param currentContent
     * @param updatedContent
     */
    public RuleContext(String groupId, String artifactId, String artifactType, String configuration,
                       List<ContentHandle> currentContent, ContentHandle updatedContent, Map<String, ContentHandle> resolvedReferences) {
        this.groupId = groupId;
        this.artifactId = Objects.requireNonNull(artifactId);
        this.artifactType = Objects.requireNonNull(artifactType);
        this.configuration = Objects.requireNonNull(configuration);
        this.currentContent = currentContent; // Current Content will be null when creating an artifact.
        this.updatedContent = Objects.requireNonNull(updatedContent);
        this.resolvedReferences = Objects.requireNonNull(resolvedReferences);
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
     * @return the artifactType
     */
    public String getArtifactType() {
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
    public List<ContentHandle> getCurrentContent() {
        return currentContent;
    }

    /**
     * @return the updatedContent
     */
    public ContentHandle getUpdatedContent() {
        return updatedContent;
    }

    /**
     * @return the references
     */
    public Map<String, ContentHandle> getResolvedReferences() {
        return resolvedReferences;
    }
}
