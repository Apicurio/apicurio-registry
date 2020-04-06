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

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

/**
 * Implements the {@link RulesService} interface.
 *
 * @author Ales Justin
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
public class RulesServiceImpl implements RulesService {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RuleExecutorFactory factory;

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRules(java.lang.String, io.apicurio.registry.types.ArtifactType, ContentHandle, io.apicurio.registry.rules.RuleApplicationType)
     */
    @Override
    public void applyRules(String artifactId, ArtifactType artifactType, ContentHandle artifactContent,
                           RuleApplicationType ruleApplicationType) throws RuleViolationException {
        boolean useGlobalRules = false;
        @SuppressWarnings("unchecked")
        List<RuleType> rules = Collections.EMPTY_LIST;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            rules = storage.getArtifactRules(artifactId);
        }
        if (rules.isEmpty()) {
            rules = storage.getGlobalRules();
            useGlobalRules = true;
        }
        if (rules.isEmpty()) {
            return;
        }
        ContentHandle currentArtifactContent = null;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            StoredArtifact currentArtifact = storage.getArtifact(artifactId);
            currentArtifactContent = currentArtifact.getContent();
        }
        for (RuleType ruleType : rules) {
            RuleConfigurationDto configurationDto = useGlobalRules ?
                                                    storage.getGlobalRule(ruleType) : storage.getArtifactRule(artifactId, ruleType);
            RuleExecutor executor = factory.createExecutor(ruleType);
            RuleContext context = new RuleContext(artifactId, artifactType, configurationDto.getConfiguration(),
                                                  currentArtifactContent, artifactContent);
            executor.execute(context);
        }
    }

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRule(java.lang.String, io.apicurio.registry.types.ArtifactType, ContentHandle, io.apicurio.registry.types.RuleType, java.lang.String, io.apicurio.registry.rules.RuleApplicationType)
     */
    @Override
    public void applyRule(String artifactId, ArtifactType artifactType, ContentHandle artifactContent,
                          RuleType ruleType, String ruleConfiguration, RuleApplicationType ruleApplicationType)
    throws RuleViolationException {
        ContentHandle currentArtifactContent = null;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            StoredArtifact currentArtifact = storage.getArtifact(artifactId);
            currentArtifactContent = currentArtifact.getContent();
        }
        applyRule(artifactId, artifactType, currentArtifactContent, artifactContent, ruleType, ruleConfiguration);
    }

    /**
     * Applies a single rule.  Throws an exception if the rule is violated.
     *
     * @param artifactId
     * @param artifactType
     * @param currentContent
     * @param updatedContent
     * @param ruleType
     * @param ruleConfiguration
     */
    private void applyRule(String artifactId, ArtifactType artifactType, ContentHandle currentContent,
                           ContentHandle updatedContent, RuleType ruleType, String ruleConfiguration) {
        RuleExecutor executor = factory.createExecutor(ruleType);
        RuleContext context = new RuleContext(artifactId, artifactType, ruleConfiguration, currentContent, updatedContent);
        executor.execute(context);
    }


    @Override
    public void applyRule(String artifactId, long artifactVersion, ArtifactType artifactType, ContentHandle updatedContent)
            throws RuleViolationException {
        StoredArtifact versionContent = storage.getArtifactVersion(artifactId, artifactVersion);
        // Get the rules for this artifact
        for (RuleType ruleType : storage.getGlobalRules()) {
            RuleConfigurationDto configurationDto = storage.getGlobalRule(ruleType);
            applyRule(artifactId, artifactType, versionContent.getContent(), updatedContent, ruleType, configurationDto.getConfiguration());
        }
        for (RuleType ruleType : storage.getArtifactRules(artifactId)) {
            RuleConfigurationDto configurationDto = storage.getArtifactRule(artifactId, ruleType);
            applyRule(artifactId, artifactType, versionContent.getContent(), updatedContent, ruleType, configurationDto.getConfiguration());
        }
    }
}
