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
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.UnprocessableSchemaException;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.error.VersionNotFoundException;
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
    public CompatibilityCheckResponse checkAllCompatibility(String subject, Boolean verbose, String groupId, RegisterSchemaRequest request) {
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
            CompatibilityCheckResponse response = new CompatibilityCheckResponse();
            response.setIsCompatible(true);

            return response;
        }
        catch (RuleViolationException ex) {
            if (fverbose) {
                //FIXME:carnalca handle verbose parameter
                CompatibilityCheckResponse response = new CompatibilityCheckResponse();
                response.setIsCompatible(false);
                return response;
            }
            else {
                CompatibilityCheckResponse response = new CompatibilityCheckResponse();
                response.setIsCompatible(false);
                return response;
            }
        }
        catch (UnprocessableSchemaException ex) {
            throw new UnprocessableEntityException(ex.getMessage());
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public CompatibilityCheckResponse checkCompatibility(String subject, String versionString, Boolean verbose, String groupId, RegisterSchemaRequest request) {
        final boolean fverbose = verbose == null ? Boolean.FALSE : verbose;
        final GA ga = getGA(groupId, subject);

        try {
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
                            CompatibilityCheckResponse response = new CompatibilityCheckResponse();
                            response.setIsCompatible(true);
                            return response;
                        } catch (RuleViolationException ex) {
                            if (fverbose) {
                                //FIXME:carnalca handle verbose parameter
                                CompatibilityCheckResponse response = new CompatibilityCheckResponse();
                                response.setIsCompatible(false);
                                return response;
                            } else {
                                CompatibilityCheckResponse response = new CompatibilityCheckResponse();
                                response.setIsCompatible(false);
                                return response;
                            }
                        } catch (UnprocessableSchemaException ex) {
                            throw new UnprocessableEntityException(ex.getMessage());
                        }
                    });
        } catch (VersionNotFoundException vnfe) {
            // Fix for Issue 6089: https://github.com/Apicurio/apicurio-registry/issues/6089
            // Click and read and be amazed.
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
