/*
 * Copyright 2023 Red Hat
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

package io.apicurio.registry.rules.integrity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Logged
public class IntegrityRuleExecutor implements RuleExecutor {

    @Inject
    ArtifactTypeUtilProviderFactory factory;
    
    /**
     * @see io.apicurio.registry.rules.RuleExecutor#execute(io.apicurio.registry.rules.RuleContext)
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {
        Set<IntegrityLevel> levels = parseConfig(context.getConfiguration());
        
        // Make sure that the user has included mappings for all references in the content of the artifact.
        if (levels.contains(IntegrityLevel.FULL) || levels.contains(IntegrityLevel.ALL_REFS_MAPPED)) {
            // Not yet implemented - needs artifact type specific logic to extract the full list of
            // references that must be mapped from the artifact version content
            verifyAllReferencesHaveMappings(context);
        }

        // Make sure that the included references all actually exist in the registry.
        if (levels.contains(IntegrityLevel.FULL) || levels.contains(IntegrityLevel.REFS_EXIST)) {
            validateReferencesExist(context);
        }

        // Make sure that there are no duplicate mappings
        if (levels.contains(IntegrityLevel.FULL) || levels.contains(IntegrityLevel.NO_DUPLICATES)) {
            checkForDuplicateReferences(context);
        }
    }

    private void verifyAllReferencesHaveMappings(RuleContext context) throws RuleViolationException {
        ArtifactTypeUtilProvider artifactTypeProvider = factory.getArtifactTypeProvider(context.getArtifactType());
        ContentValidator validator = artifactTypeProvider.getContentValidator();
        validator.validateReferences(context.getUpdatedContent(), context.getReferences());
    }

    private void validateReferencesExist(RuleContext context) throws RuleViolationException {
        List<ArtifactReference> references = context.getReferences();
        Map<String, ContentHandle> resolvedReferences = context.getResolvedReferences();
        
        Set<RuleViolation> causes = new HashSet<>();
        references.forEach(ref -> {
            if (!resolvedReferences.containsKey(ref.getName())) {
                RuleViolation violation = new RuleViolation();
                violation.setContext(ref.getName());
                violation.setDescription(String.format("Referenced artifact (%s/%s @ %s) does not yet exist in the registry.", ref.getGroupId(), ref.getArtifactId(), ref.getVersion()));
                causes.add(violation);
            }
        });
        if (!causes.isEmpty()) {
            throw new RuleViolationException("Referenced artifact does not exist.", RuleType.INTEGRITY, 
                    IntegrityLevel.REFS_EXIST.name(), causes);
        }
        
    }

    private void checkForDuplicateReferences(RuleContext context) throws RuleViolationException {
        List<ArtifactReference> references = context.getReferences();
        if (references != null && references.size() > 1) {
            Set<String> refNames = new HashSet<>();
            Set<RuleViolation> causes = new HashSet<>();
            references.forEach(ref -> {
                if (refNames.contains(ref.getName())) {
                    RuleViolation violation = new RuleViolation();
                    violation.setContext(ref.getName());
                    violation.setDescription("Duplicate mapping for artifact reference with name: " + ref.getName());
                    causes.add(violation);
                }
                refNames.add(ref.getName());
            });
            if (!causes.isEmpty()) {
                throw new RuleViolationException("Duplicate artifact reference(s) detected.", RuleType.INTEGRITY, 
                        IntegrityLevel.NO_DUPLICATES.name(), causes);
            }
        }
    }
    

    /**
     * @param configuration
     */
    private Set<IntegrityLevel> parseConfig(String configuration) {
        Set<IntegrityLevel> levels = new HashSet<>();
        if (configuration != null) {
            String[] split = configuration.split(",");
            for (String cvalue : split) {
                levels.add(IntegrityLevel.valueOf(cvalue.trim()));
            }
        }
        return levels;
    }

}
