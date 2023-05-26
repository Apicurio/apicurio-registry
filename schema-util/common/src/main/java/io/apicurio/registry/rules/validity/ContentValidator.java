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

package io.apicurio.registry.rules.validity;

import java.util.List;
import java.util.Map;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;

/**
 * Validates content.  Syntax and semantic validations are possible based on configuration.  An
 * implementation of this interface should exist for each content-type supported by the registry.
 * 
 * Also provides validation of references.
 *
 * @author eric.wittmann@gmail.com
 */
public interface ContentValidator {

    /**
     * Called to validate the given content.
     *
     * @param level           the level
     * @param artifactContent the content
     * @param resolvedReferences a map containing the resolved references
     * @throws RuleViolationException for any invalid content
     */
    public void validate(ValidityLevel level, ContentHandle artifactContent, Map<String, ContentHandle> resolvedReferences) throws RuleViolationException;
    
    /**
     * Ensures that all references in the content are represented in the list of passed references.  This is used
     * to ensure that the content does not have any references that are unmapped.
     * 
     * @param artifactContent
     * @param references
     * @throws RuleViolationException
     */
    public void validateReferences(ContentHandle artifactContent, List<ArtifactReference> references) throws RuleViolationException;

}
