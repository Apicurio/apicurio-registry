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

package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An interface that is used to determine whether a proposed artifact's content is compatible and return a set of
 * incompatible differences
 * with older version(s) of the same content, based on a given compatibility level.
 *
 * @author Ales Justin
 */
public interface CompatibilityChecker {
    /**
     * @param compatibilityLevel MUST NOT be null
     * @param existingArtifacts  MUST NOT be null and MUST NOT contain null elements,
     *                           but may be empty if the rule is executed and the artifact does not exist
     *                           (e.g. a global COMPATIBILITY rule with <code>io.apicurio.registry.rules.RuleApplicationType#CREATE</code>)
     * @param proposedArtifact   MUST NOT be null
     */
    CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact);

    /**
     * @param compatibilityLevel MUST NOT be null
     * @param existingArtifacts  MUST NOT be null and MUST NOT contain null elements,
     *                           but may be empty if the rule is executed and the artifact does not exist
     *                           (e.g. a global COMPATIBILITY rule with <code>io.apicurio.registry.rules.RuleApplicationType#CREATE</code>)
     * @param proposedArtifact   MUST NOT be null
     */
    default CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<String> existingArtifacts, String proposedArtifact) {
        final List<ContentHandle> contentHandles = existingArtifacts.stream().map(ContentHandle::create).collect(Collectors.toList());
        return testCompatibility(compatibilityLevel, contentHandles, ContentHandle.create(proposedArtifact));
    }
}