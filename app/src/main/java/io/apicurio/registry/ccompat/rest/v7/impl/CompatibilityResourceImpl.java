package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.ccompat.rest.v7.CompatibilityResource;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.UnprocessableSchemaException;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import jakarta.interceptor.Interceptors;

import java.util.Collections;
import java.util.List;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class CompatibilityResourceImpl extends AbstractResource implements CompatibilityResource {

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityCheckResponse testCompatibilityBySubjectName(String subject, SchemaContent request,
            Boolean verbose, String groupId) throws Exception {
        final GA ga = getGA(groupId, subject);
        final boolean fverbose = verbose == null ? Boolean.FALSE : verbose;
        try {
            final List<String> versions = storage.getArtifactVersions(ga.getRawGroupIdWithNull(),
                    ga.getRawArtifactId());
            for (String version : versions) {
                final ArtifactVersionMetaDataDto artifactVersionMetaData = storage.getArtifactVersionMetaData(
                        ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version);
                // Assume the content type of the SchemaContent is the same as the previous version.
                String contentType = ContentTypes.APPLICATION_JSON;
                if (artifactVersionMetaData.getArtifactType().equals(ArtifactType.PROTOBUF)) {
                    contentType = ContentTypes.APPLICATION_PROTOBUF;
                }
                TypedContent typedContent = TypedContent.create(ContentHandle.create(request.getSchema()),
                        contentType);
                rulesService.applyRules(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version,
                        artifactVersionMetaData.getArtifactType(), typedContent, Collections.emptyList(),
                        Collections.emptyMap());
            }
            return CompatibilityCheckResponse.IS_COMPATIBLE;
        } catch (RuleViolationException ex) {
            if (fverbose) {
                return new CompatibilityCheckResponse(false, ex.getMessage());
            } else {
                return CompatibilityCheckResponse.IS_NOT_COMPATIBLE;
            }
        } catch (UnprocessableSchemaException ex) {
            throw new UnprocessableEntityException(ex.getMessage());
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityCheckResponse testCompatibilityByVersion(String subject, String versionString,
            SchemaContent request, Boolean verbose, String groupId) throws Exception {
        final boolean fverbose = verbose == null ? Boolean.FALSE : verbose;
        final GA ga = getGA(groupId, subject);

        return parseVersionString(ga.getRawArtifactId(), versionString, ga.getRawGroupIdWithNull(),
                version -> {
                    try {
                        final ArtifactVersionMetaDataDto artifact = storage.getArtifactVersionMetaData(
                                ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version);
                        // Assume the content type of the SchemaContent is correct based on the artifact type.
                        String contentType = ContentTypes.APPLICATION_JSON;
                        if (artifact.getArtifactType().equals(ArtifactType.PROTOBUF)) {
                            contentType = ContentTypes.APPLICATION_PROTOBUF;
                        }
                        TypedContent typedContent = TypedContent
                                .create(ContentHandle.create(request.getSchema()), contentType);
                        rulesService.applyRules(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version,
                                artifact.getArtifactType(), typedContent, Collections.emptyList(),
                                Collections.emptyMap());
                        return CompatibilityCheckResponse.IS_COMPATIBLE;
                    } catch (RuleViolationException ex) {
                        if (fverbose) {
                            return new CompatibilityCheckResponse(false, ex.getMessage());
                        } else {
                            return CompatibilityCheckResponse.IS_NOT_COMPATIBLE;
                        }
                    } catch (UnprocessableSchemaException ex) {
                        throw new UnprocessableEntityException(ex.getMessage());
                    }
                });
    }
}
