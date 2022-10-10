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

package io.apicurio.registry.ccompat.rest.v6.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelDto;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelParamDto;
import io.apicurio.registry.ccompat.rest.v6.ConfigResource;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.common.apps.logging.audit.AuditingConstants;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;

import javax.interceptor.Interceptors;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class ConfigResourceImpl extends AbstractResource implements ConfigResource {


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
            return new CompatibilityLevelParamDto(CompatibilityLevelDto.Level.NONE.name());
        }
    }


    private void updateCompatibilityLevel(CompatibilityLevelDto.Level level,
                                          Consumer<RuleConfigurationDto> updater,
                                          Runnable deleter) {
        if (level == CompatibilityLevelDto.Level.NONE) {
            // delete the rule
            deleter.run();
        } else {
            String levelString = level.getStringValue();
            try {
                CompatibilityLevel.valueOf(levelString);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Illegal compatibility level: " + levelString);
            }
            updater.accept(RuleConfigurationDto.builder()
                    .configuration(levelString).build()); // TODO config should take CompatibilityLevel as param
        }
    }


    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public CompatibilityLevelParamDto getGlobalCompatibilityLevel() {
        return getCompatibilityLevel(() ->
                facade.getGlobalRule(RuleType.COMPATIBILITY).getConfiguration());
    }


    @Override
    @Audited(extractParameters = {"0", AuditingConstants.KEY_RULE})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public CompatibilityLevelDto updateGlobalCompatibilityLevel(
            CompatibilityLevelDto request) {

        updateCompatibilityLevel(request.getCompatibility(),
                dto -> facade.createOrUpdateGlobalRule(RuleType.COMPATIBILITY, dto),
                () -> facade.deleteGlobalRule(RuleType.COMPATIBILITY));
        return request;
    }


    @Override
    @Audited(extractParameters = {"0", AuditingConstants.KEY_ARTIFACT_ID, "1", AuditingConstants.KEY_RULE})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityLevelDto updateSubjectCompatibilityLevel(
            String subject,
            CompatibilityLevelDto request) {
        updateCompatibilityLevel(request.getCompatibility(),
                dto -> facade.createOrUpdateArtifactRule(subject, RuleType.COMPATIBILITY, dto),
                () -> facade.deleteArtifactRule(subject, RuleType.COMPATIBILITY));
        return request;
    }

    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public CompatibilityLevelParamDto getSubjectCompatibilityLevel(String subject) {
        return getCompatibilityLevel(() ->
                facade.getArtifactRule(subject, RuleType.COMPATIBILITY).getConfiguration());
    }
}
