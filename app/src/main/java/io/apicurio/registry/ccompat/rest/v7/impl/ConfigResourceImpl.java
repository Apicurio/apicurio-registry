package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.rest.v7.ConfigResource;
import io.apicurio.registry.ccompat.rest.v7.beans.GlobalConfigResponse;
import io.apicurio.registry.ccompat.rest.v7.beans.ConfigUpdateRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.SubjectConfigResponse;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.logging.audit.Audited;
import io.apicurio.registry.logging.audit.AuditingConstants;
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

    //FIMXE: The configuration implementation is not complete. We only do global and artifact level compatibility. Confluent has more options.

    private GlobalConfigResponse getCompatibilityLevel(Supplier<String> supplyLevel) {
        try {
            // We're assuming the configuration == compatibility level
            // TODO make it more explicit
            GlobalConfigResponse response = new GlobalConfigResponse();
            response.setCompatibilityLevel(Optional.of(CompatibilityLevel.valueOf(supplyLevel.get())).get().name());
            return response;
        }
        catch (RuleNotFoundException ex) {
            GlobalConfigResponse response = new GlobalConfigResponse();
            response.setCompatibilityLevel(CompatibilityLevel.NONE.name());
            return response;
        }
    }

    private <X extends Exception> void updateCompatibilityLevel(String level,
                                                                Runnable1Ex<RuleConfigurationDto, X> updater, RunnableEx<X> deleter) throws X {
        if (CompatibilityLevel.NONE.name().equals(level)) {
            // delete the rule
            deleter.run();
        }
        else {
            try {
                CompatibilityLevel.valueOf(level);
            }
            catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Illegal compatibility level: " + level);
            }
            updater.run(RuleConfigurationDto.builder().configuration(level).build()); // TODO config
            // should take
            // CompatibilityLevel
            // as param
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public GlobalConfigResponse getGlobalConfig() {
        return getCompatibilityLevel(() -> storage.getGlobalRule(RuleType.COMPATIBILITY).getConfiguration());
    }

    @Override
    @Audited(extractParameters = { "0", AuditingConstants.KEY_RULE })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public GlobalConfigResponse updateGlobalConfig(ConfigUpdateRequest request) {
        updateCompatibilityLevel(request.getCompatibility(), dto -> {
            if (!doesGlobalRuleExist(RuleType.COMPATIBILITY)) {
                storage.createGlobalRule(RuleType.COMPATIBILITY, dto);
            }
            else {
                storage.updateGlobalRule(RuleType.COMPATIBILITY, dto);
            }
        }, () -> storage.deleteGlobalRule(RuleType.COMPATIBILITY));

        GlobalConfigResponse response = new GlobalConfigResponse();
        response.setCompatibilityLevel(request.getCompatibility());
        return response;
    }

    @Override
    @Audited(extractParameters = { "0", AuditingConstants.KEY_ARTIFACT_ID, "1", AuditingConstants.KEY_RULE })
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public GlobalConfigResponse updateSubjectConfig(String subject, String groupId, ConfigUpdateRequest request) {
        final GA ga = getGA(groupId, subject);

        updateCompatibilityLevel(request.getCompatibility(), dto -> {
            if (!doesArtifactRuleExist(ga.getRawArtifactId(), RuleType.COMPATIBILITY,
                    ga.getRawGroupIdWithNull())) {
                storage.createArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                        RuleType.COMPATIBILITY, dto);
            }
            else {
                storage.updateArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                        RuleType.COMPATIBILITY, dto);
            }
        }, () -> {
            try {
                storage.deleteArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                        RuleType.COMPATIBILITY);
            }
            catch (RuleNotFoundException e) {
                // Ignore, fail only when the artifact is not found
            }
        });

        GlobalConfigResponse response = new GlobalConfigResponse();
        response.setCompatibilityLevel(request.getCompatibility());
        return response;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public SubjectConfigResponse getSubjectConfig(String subject, Boolean defaultToGlobal,
                                                  String groupId) {
        final GA ga = getGA(groupId, subject);
        SubjectConfigResponse response = new SubjectConfigResponse();

        response.setCompatibilityLevel(SubjectConfigResponse.CompatibilityLevel.valueOf(getCompatibilityLevel(() -> {
            String level;
            try {
                level = storage.getArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                        RuleType.COMPATIBILITY).getConfiguration();
            }
            catch (RuleNotFoundException e) {
                if (defaultToGlobal != null && defaultToGlobal) {
                    level = storage.getGlobalRule(RuleType.COMPATIBILITY).getConfiguration();
                }
                else {
                    throw e;
                }
            }
            return level;
        }).getCompatibilityLevel()));

        return response;
    }

    @Override
    @Audited(extractParameters = { "0", AuditingConstants.KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public GlobalConfigResponse deleteSubjectConfig(String subject, String groupId) {
        final GA ga = getGA(groupId, subject);
        final GlobalConfigResponse compatibilityLevel = getCompatibilityLevel(() -> storage
                .getArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), RuleType.COMPATIBILITY)
                .getConfiguration());
        if (!CompatibilityLevel.NONE.name().equals(compatibilityLevel.getCompatibilityLevel())) {
            storage.deleteArtifactRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                    RuleType.COMPATIBILITY);
        }
        return compatibilityLevel;
    }
}
