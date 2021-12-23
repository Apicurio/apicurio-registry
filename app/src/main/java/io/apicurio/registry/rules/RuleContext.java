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

import io.apicurio.registry.types.ArtifactType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import io.apicurio.registry.content.ContentHandle;

/**
 * Contains all of the information needed by a rule executor, including the rule-specific
 * configuration, current and updated content, and any other meta-data needed.
 *
 * @author Ales Justin
 */
@AllArgsConstructor
@Builder
public class RuleContext {

    /**
     * Rule identifier, could be the RuleType or the custom rule id.
     */
    private final String ruleId;
    private final String configuration;

    private final String groupId;
    private final String artifactId;
    private final ArtifactType artifactType;
    private final ContentHandle currentContent;
    private final ContentHandle updatedContent;

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
    public ContentHandle getCurrentContent() {
        return currentContent;
    }

    /**
     * @return the updatedContent
     */
    public ContentHandle getUpdatedContent() {
        return updatedContent;
    }

    /**
     * @return the ruleId
     */
    public String getRuleId() {
        return ruleId;
    }
}
