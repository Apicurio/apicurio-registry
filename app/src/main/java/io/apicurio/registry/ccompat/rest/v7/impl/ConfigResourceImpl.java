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
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.Functional.Runnable1Ex;
import io.apicurio.registry.utils.Functional.RunnableEx;
import jakarta.interceptor.Interceptors;

import java.util.Optional;
import java.util.function.Supplier;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ConfigResourceImpl extends AbstractResource implements ConfigResource {

    private CompatibilityLevelParamDto getCompatibilityLevel(Supplier<String> supplyLevel) {
        try {
            // We're assuming the configuration == compatibility level
            // TODO make it more explicit
            return new CompatibilityLevelParamDto(
                    Optional.of(CompatibilityLevel.valueOf(supplyLevel.get())).get().name());
        } catch (RuleNotFoundException ex) {
            return new CompatibilityLevelParamDto(CompatibilityLevelDto.Level.NONE.name());
        }
    }

    private <X extends Exception> void updateCompatibilityLevel(CompatibilityLevelDto.Level level,
            Runnable1Ex<RuleConfigurationDto, X> updater, RunnableEx<X> deleter) throws X {
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
            updater.run(RuleConfigurationDto.builder().configuration(levelString).build()); // TODO config
                                                                                            // should take
                                                                                            // CompatibilityLevel
                                                                                            // as param
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public CompatibilityLevelParamDto getGlobalCompatibilityLevel() {
        return getCompatibilityLevel(() -> storage.getGlobalRule(RuleType.COMPATIBILITY).getConfiguration());
    }

    @Override
    @Audited(extractParameters = { "0", AuditingConstants.KEY_RULE })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public CompatibilityLevelDto updateGlobalCompatibilityLevel(CompatibilityLevelDto request) {
        updateCompatibilityLevel(request.getCompatibility(), dto -> {
            if (!doesGlobalRuleExist(RuleType.COMPATIBILITY)) {
                storage.createGlobalRule(RuleType.COMPATIBILITY, dto);
            } else {
                storage.updateGlobalRule(RuleType.COMPATIBILITY, dto);
            }
        }, () -> storage.deleteGlobalRule(RuleType.COMPATIBILITY));
        return request;
    }

    @Override
    @Audited(extractParameters = { "0", AuditingConstants.KEY_ARTIFACT_ID, "1", AuditingConstants.KEY_RULE })
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityLevelDto updateSubjectCompatibilityLevel(String subject,
            CompatibilityLevelDto request, String groupId) {
        final GA ga = getGA(groupId, subject);

        updateCompatibilityLevel(request.getCompatibility(), dto -> {
            if (!doesArtifactRuleExist(ga.getRawArtifactId(), RuleType.COMPATIBILITY,
                    ga.getRawGroupIdWithNull())) {
                storage.createArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                        RuleType.COMPATIBILITY, dto);
            } else {
                storage.updateArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                        RuleType.COMPATIBILITY, dto);
            }
        }, () -> {
            try {
                storage.deleteArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                        RuleType.COMPATIBILITY);
            } catch (RuleNotFoundException e) {
                // Ignore, fail only when the artifact is not found
            }
        });
        return request;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public CompatibilityLevelParamDto getSubjectCompatibilityLevel(String subject, Boolean defaultToGlobal,
            String groupId) {
        final GA ga = getGA(groupId, subject);
        return getCompatibilityLevel(() -> {
            String level;
            try {
                level = storage.getArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                        RuleType.COMPATIBILITY).getConfiguration();
            } catch (RuleNotFoundException e) {
                if (defaultToGlobal != null && defaultToGlobal) {
                    level = storage.getGlobalRule(RuleType.COMPATIBILITY).getConfiguration();
                } else {
                    throw e;
                }
            }
            return level;
        });
    }

    @Override
    @Audited(extractParameters = { "0", AuditingConstants.KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityLevelParamDto deleteSubjectCompatibility(String subject, String groupId) {
        final GA ga = getGA(groupId, subject);
        final CompatibilityLevelParamDto compatibilityLevel = getCompatibilityLevel(() -> storage
                .getArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), RuleType.COMPATIBILITY)
                .getConfiguration());
        if (!CompatibilityLevel.NONE.name().equals(compatibilityLevel.getCompatibilityLevel())) {
            storage.deleteArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                    RuleType.COMPATIBILITY);
        }
        return compatibilityLevel;
    }
}
