/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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
import io.apicurio.registry.storage.dto.ArtifactIdDto;
import io.apicurio.registry.storage.dto.CustomRuleDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implements the {@link RulesService} interface.
 *
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ApplicationScoped
public class RulesServiceImpl implements RulesService {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RuleExecutorFactory factory;

    @Inject
    RulesProperties rulesProperties;

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRules(java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.rules.RuleApplicationType)
     */
    @Override
    public void applyRules(String groupId, String artifactId, ArtifactType artifactType, ContentHandle artifactContent,
                          RuleApplicationType ruleApplicationType) throws RuleViolationException {
        @SuppressWarnings("unchecked")
        List<RuleType> rules = Collections.EMPTY_LIST;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            rules = storage.getArtifactRules(groupId, artifactId);
        }
        ContentHandle currentArtifactContent = null;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            StoredArtifactDto currentArtifact = storage.getArtifact(groupId, artifactId);
            currentArtifactContent = currentArtifact.getContent();
        }

        applyRulesAndCustomRules(groupId, artifactId, artifactType, currentArtifactContent, artifactContent, rules);
    }

    private void applyRulesAndCustomRules(String groupId, String artifactId, ArtifactType artifactType,
            ContentHandle currentArtifactContent, ContentHandle updatedArtifactContent,
            List<RuleType> artifactRules) {

        applyArtifactOrGlobalRules(groupId, artifactId, artifactType, currentArtifactContent, updatedArtifactContent, artifactRules);

        applyCustomRules(groupId, artifactId, artifactType, currentArtifactContent, updatedArtifactContent);

    }

    private void applyArtifactOrGlobalRules(String groupId, String artifactId, ArtifactType artifactType,
            ContentHandle currentArtifactContent, ContentHandle updatedArtifactContent,
            List<RuleType> artifactRules) {
        Map<RuleType, RuleConfigurationDto> globalOrArtifactRulesMap = artifactRules.stream()
            .collect(Collectors.toMap(ruleType -> ruleType, ruleType -> storage.getArtifactRule(groupId, artifactId, ruleType)));

        if (globalOrArtifactRulesMap.isEmpty()) {
            List<RuleType> globalRules = storage.getGlobalRules();
            globalOrArtifactRulesMap = globalRules.stream()
                .collect(Collectors.toMap(ruleType -> ruleType, storage::getGlobalRule));

            // Add any default global rules to the map (after filtering out any global rules from artifactStore)
            Map<RuleType, RuleConfigurationDto>  filteredDefaultGlobalRulesMap = rulesProperties.getFilteredDefaultGlobalRules(globalRules).stream()
                .collect(Collectors.toMap(ruleType -> ruleType, rulesProperties::getDefaultGlobalRuleConfiguration));
            globalOrArtifactRulesMap.putAll(filteredDefaultGlobalRulesMap);
        }

        if (globalOrArtifactRulesMap.isEmpty()) {
            return;
        }

        for (RuleType ruleType : globalOrArtifactRulesMap.keySet()) {
            applyRule(groupId, artifactId, artifactType, currentArtifactContent, updatedArtifactContent, ruleType, globalOrArtifactRulesMap.get(ruleType).getConfiguration());
        }
    }

    private void applyCustomRules(String groupId, String artifactId, ArtifactType artifactType,
            ContentHandle currentArtifactContent, ContentHandle updatedArtifactContent) {
        Map<String, CustomRuleDto> allCustomRulesConfig = storage.listArtifactAvailableCustomRules(groupId, artifactId)
            .stream()
            .collect(Collectors.toMap(cr -> cr.getId(), cr -> cr));

        if (allCustomRulesConfig.isEmpty()) {
            return;
        }

        List<CustomRuleDto> customRulesToExecute = storage.listCustomRuleBindings(Optional.of(ArtifactIdDto.of(groupId, artifactId)))
                .stream()
                .map(crb -> allCustomRulesConfig.get(crb.getCustomRuleId()))
                .collect(Collectors.toList());

        if (customRulesToExecute.isEmpty()) {
            customRulesToExecute = storage.listCustomRuleBindings(Optional.empty())
                .stream()
                .map(crb -> allCustomRulesConfig.get(crb.getCustomRuleId()))
                .filter(cr -> cr != null)
                .collect(Collectors.toList());
        }

        if (customRulesToExecute.isEmpty()) {
            return;
        }

        for (CustomRuleDto cr : customRulesToExecute) {
            RuleExecutor executor = factory.getCustomRuleExecutor(cr.getCustomRuleType());
            executor.execute(RuleContext.builder()
                                .ruleId(cr.getId())
                                .configuration(cr.getConfig())
                                .groupId(groupId)
                                .artifactId(artifactId)
                                .artifactType(artifactType)
                                .currentContent(currentArtifactContent)
                                .updatedContent(updatedArtifactContent)
                                .build());
        }
    }

    /**
     * Applies a single rule.  Throws an exception if the rule is violated.
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param currentContent
     * @param updatedContent
     * @param ruleType
     * @param ruleConfiguration
     */
    private void applyRule(String groupId, String artifactId, ArtifactType artifactType, ContentHandle currentContent,
                           ContentHandle updatedContent, RuleType ruleType, String ruleConfiguration) {
        RuleExecutor executor = factory.createExecutor(ruleType);

        executor.execute(RuleContext.builder()
                            .ruleId(ruleType.value())
                            .configuration(ruleConfiguration)
                            .groupId(groupId)
                            .artifactId(artifactId)
                            .artifactType(artifactType)
                            .currentContent(currentContent)
                            .updatedContent(updatedContent)
                            .build());
    }

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRules(java.lang.String, java.lang.String, long, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public void applyRules(String groupId, String artifactId, String artifactVersion, ArtifactType artifactType, ContentHandle updatedContent)
            throws RuleViolationException {
        StoredArtifactDto versionContent = storage.getArtifactVersion(groupId, artifactId, artifactVersion);
        applyRulesAndCustomRules(groupId, artifactId, artifactType, versionContent.getContent(), updatedContent, storage.getArtifactRules(groupId, artifactId));
    }
}
