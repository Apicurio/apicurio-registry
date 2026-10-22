package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.ccompat.rest.v7.CompatibilityResource;
import io.apicurio.registry.ccompat.rest.v7.beans.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.rules.RulesProperties;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

import java.util.Collections;
import java.util.List;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class CompatibilityResourceImpl extends AbstractResource implements CompatibilityResource {

    @Inject
    RulesProperties rulesProperties;

    private boolean isCompatibilityRuleConfigured(String groupId, String artifactId) {
        try {
            storage.getArtifactRule(groupId, artifactId, RuleType.COMPATIBILITY);
            return true;
        } catch (RuleNotFoundException e) {
            // No artifact-level rule
        }
        try {
            if (storage.isGroupExists(groupId)) {
                storage.getGroupRule(groupId, RuleType.COMPATIBILITY);
                return true;
            }
        } catch (RuleNotFoundException e) {
            // No group-level rule
        }
        try {
            storage.getGlobalRule(RuleType.COMPATIBILITY);
            return true;
        } catch (RuleNotFoundException e) {
            // No global rule
        }
        return rulesProperties.isDefaultGlobalRuleConfigured(RuleType.COMPATIBILITY);
    }

    private void validateSchemaContent(String schema, String artifactType) {
        if (schema == null || artifactType == null) {
            throw new UnprocessableEntityException("Schema and artifact type must not be null");
        }
        String contentType = artifactType.equals(ArtifactType.PROTOBUF)
                ? ContentTypes.APPLICATION_PROTOBUF : ContentTypes.APPLICATION_JSON;
        TypedContent typedContent = TypedContent.create(ContentHandle.create(schema), contentType);
        try {
            factory.getArtifactTypeProvider(artifactType).getContentValidator()
                    .validate(io.apicurio.registry.rules.validity.ValidityLevel.SYNTAX_ONLY,
                            typedContent, Collections.emptyMap());
        } catch (Exception ex) {
            throw new UnprocessableEntityException(
                    "Invalid schema: " + ex.getMessage());
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityCheckResponse checkAllCompatibility(String subject, Boolean verbose, Boolean normalize, String groupId, RegisterSchemaRequest request) {
        final GA ga = getGA(groupId, subject);
        try {
            final List<String> versions = storage.getArtifactVersions(ga.getRawGroupIdWithNull(),
                    ga.getRawArtifactId());
            for (String version : versions) {
                final ArtifactVersionMetaDataDto artifactVersionMetaData = storage.getArtifactVersionMetaData(
                        ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version);
                String contentType = ContentTypes.APPLICATION_JSON;
                if (artifactVersionMetaData.getArtifactType().equals(ArtifactType.PROTOBUF)) {
                    contentType = ContentTypes.APPLICATION_PROTOBUF;
                }

                validateSchemaContent(request.getSchema(), artifactVersionMetaData.getArtifactType());

                TypedContent typedContent = TypedContent.create(ContentHandle.create(request.getSchema()),
                        contentType);

                if (!isCompatibilityRuleConfigured(ga.getRawGroupIdWithNull(), ga.getRawArtifactId())) {
                    rulesService.applyRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                            artifactVersionMetaData.getArtifactType(), typedContent,
                            RuleType.COMPATIBILITY, CompatibilityLevel.BACKWARD.name(),
                            io.apicurio.registry.rules.RuleApplicationType.UPDATE,
                            Collections.emptyList(), Collections.emptyMap());
                } else {
                    rulesService.applyRules(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version,
                            artifactVersionMetaData.getArtifactType(), typedContent, Collections.emptyList(),
                            Collections.emptyMap());
                }
            }
            CompatibilityCheckResponse response = new CompatibilityCheckResponse();
            response.setIsCompatible(true);
            return response;
        }
        catch (RuleViolationException ex) {
            CompatibilityCheckResponse response = new CompatibilityCheckResponse();
            response.setIsCompatible(false);
            return response;
        }
        catch (UnprocessableSchemaException ex) {
            throw new UnprocessableEntityException(ex.getMessage());
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public CompatibilityCheckResponse checkCompatibility(String subject, String versionString, Boolean verbose, Boolean normalize, String groupId, RegisterSchemaRequest request) {
        final GA ga = getGA(groupId, subject);

        try {
            return parseVersionString(ga.getRawArtifactId(), versionString, ga.getRawGroupIdWithNull(),
                    version -> {
                        try {
                            final ArtifactVersionMetaDataDto artifact = storage.getArtifactVersionMetaData(
                                    ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version);
                            String contentType = ContentTypes.APPLICATION_JSON;
                            if (artifact.getArtifactType().equals(ArtifactType.PROTOBUF)) {
                                contentType = ContentTypes.APPLICATION_PROTOBUF;
                            }

                            validateSchemaContent(request.getSchema(), artifact.getArtifactType());

                            TypedContent typedContent = TypedContent
                                    .create(ContentHandle.create(request.getSchema()), contentType);

                            if (!isCompatibilityRuleConfigured(ga.getRawGroupIdWithNull(),
                                    ga.getRawArtifactId())) {
                                rulesService.applyRule(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                                        artifact.getArtifactType(), typedContent,
                                        RuleType.COMPATIBILITY, CompatibilityLevel.BACKWARD.name(),
                                        io.apicurio.registry.rules.RuleApplicationType.UPDATE,
                                        Collections.emptyList(), Collections.emptyMap());
                            } else {
                                rulesService.applyRules(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                                        version, artifact.getArtifactType(), typedContent,
                                        Collections.emptyList(), Collections.emptyMap());
                            }
                            CompatibilityCheckResponse response = new CompatibilityCheckResponse();
                            response.setIsCompatible(true);
                            return response;
                        } catch (RuleViolationException ex) {
                            CompatibilityCheckResponse response = new CompatibilityCheckResponse();
                            response.setIsCompatible(false);
                            return response;
                        } catch (UnprocessableSchemaException ex) {
                            throw new UnprocessableEntityException(ex.getMessage());
                        }
                    });
        } catch (VersionNotFoundException vnfe) {
            if (BranchId.LATEST.getRawBranchId().equals(versionString)) {
                CompatibilityCheckResponse response = new CompatibilityCheckResponse();
                response.setIsCompatible(true);
                return response;
            } else {
                throw vnfe;
            }
        }
    }
}
