/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.common.apps.logging.audit.AuditingConstants;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelDto;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelParamDto;
import io.apicurio.registry.ccompat.rest.v7.ConfigResource;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.rules.RulesProperties;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Carles Arnal
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class ConfigResourceImpl extends AbstractResource implements ConfigResource {

    @Inject
    RulesProperties rulesProperties;

    private CompatibilityLevelParamDto getCompatibilityLevel(Supplier<String> supplyLevel) {
        try {
            // We're assuming the configuration == compatibility level
            // TODO make it more explicit
            return new CompatibilityLevelParamDto(Optional.of(
                    CompatibilityLevel.valueOf(
                            supplyLevel.get()
                    )
            ).get().name());
        } catch (RuleNotFoundException ex) {
            var compatRuleDto = rulesProperties.getDefaultGlobalRuleConfiguration(RuleType.COMPATIBILITY);
            if (compatRuleDto != null) {
                // Fallback to the default global rule
                return new CompatibilityLevelParamDto(compatRuleDto.getConfiguration());
            } else {
                // Fallback to NONE if no default rule is found
                return new CompatibilityLevelParamDto(CompatibilityLevelDto.Level.NONE.name());
            }
        }
    }

    private <X extends Exception> void updateCompatibilityLevel(CompatibilityLevelDto.Level level,
                                                                Consumer<RuleConfigurationDto> updater) throws X {
        String levelString = level.getStringValue();
        try {
            CompatibilityLevel.valueOf(levelString);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Illegal compatibility level: " + levelString);
        }
        updater.accept(RuleConfigurationDto.builder()
                .configuration(levelString).build()); // TODO config should take CompatibilityLevel as param
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public CompatibilityLevelParamDto getGlobalCompatibilityLevel() {
        return getCompatibilityLevel(() -> storage.getGlobalRule(RuleType.COMPATIBILITY).getConfiguration());
    }

    @Override
    @Audited(extractParameters = {"0", AuditingConstants.KEY_RULE})
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public CompatibilityLevelDto updateGlobalCompatibilityLevel(CompatibilityLevelDto request) {
        updateCompatibilityLevel(request.getCompatibility(),
                dto -> {
                    if (!doesGlobalRuleExist(RuleType.COMPATIBILITY)) {
                        storage.createGlobalRule(RuleType.COMPATIBILITY, dto);
                    } else {
                        storage.updateGlobalRule(RuleType.COMPATIBILITY, dto);
                    }
                }
        );
        return request;
    }

    @Override
    @Audited(extractParameters = {"0", AuditingConstants.KEY_ARTIFACT_ID, "1", AuditingConstants.KEY_RULE})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityLevelDto updateSubjectCompatibilityLevel(String subject, CompatibilityLevelDto request, String groupId) {
        final GA ga = getGA(groupId, subject);

        updateCompatibilityLevel(request.getCompatibility(),
                dto -> {
                    if (!doesArtifactRuleExist(ga.getArtifactId(), RuleType.COMPATIBILITY, ga.getGroupId())) {
                        storage.createArtifactRule(ga.getGroupId(), ga.getArtifactId(), RuleType.COMPATIBILITY, dto);
                    } else {
                        storage.updateArtifactRule(ga.getGroupId(), ga.getArtifactId(), RuleType.COMPATIBILITY, dto);
                    }
                }
        );
        return request;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public CompatibilityLevelParamDto getSubjectCompatibilityLevel(String subject, String groupId) {
        final GA ga = getGA(groupId, subject);
        return getCompatibilityLevel(() -> storage.getArtifactRule(ga.getGroupId(), ga.getArtifactId(), RuleType.COMPATIBILITY).getConfiguration());
    }

    @Override
    @Audited(extractParameters = {"0", AuditingConstants.KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityLevelParamDto deleteSubjectCompatibility(String subject, String groupId) {
        final GA ga = getGA(groupId, subject);
        final CompatibilityLevelParamDto compatibilityLevel = getCompatibilityLevel(() ->
                storage.getArtifactRule(ga.getGroupId(), ga.getArtifactId(), RuleType.COMPATIBILITY).getConfiguration());
        if (!CompatibilityLevel.NONE.name().equals(compatibilityLevel.getCompatibilityLevel())) {
            storage.deleteArtifactRule(ga.getGroupId(), ga.getArtifactId(), RuleType.COMPATIBILITY);
        }
        return compatibilityLevel;
    }
}
