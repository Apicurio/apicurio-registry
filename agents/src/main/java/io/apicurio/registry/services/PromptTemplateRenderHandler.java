package io.apicurio.registry.services;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.extensions.PromptRenderHandler;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.rest.v3.beans.RenderPromptRequest;
import io.apicurio.registry.rest.v3.beans.RenderPromptResponse;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.VersionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.Map;

/**
 * Implements the v3 render endpoint for PROMPT_TEMPLATE artifact versions.
 */
@ApplicationScoped
public class PromptTemplateRenderHandler implements PromptRenderHandler {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    PromptRenderingService promptRenderingService;

    @Override
    public RenderPromptResponse render(String groupId, String artifactId, String versionExpression,
            RenderPromptRequest request) {
        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));

        // Verify the artifact exists and is of type PROMPT_TEMPLATE
        ArtifactVersionMetaDataDto versionMetaData = storage.getArtifactVersionMetaData(
                gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

        if (versionMetaData.getState() == VersionState.DISABLED) {
            throw new VersionNotFoundException(groupId, artifactId, versionExpression);
        }

        String artifactType = versionMetaData.getArtifactType();
        if (!ArtifactType.PROMPT_TEMPLATE.equals(artifactType)) {
            throw new BadRequestException(
                    "Artifact type must be PROMPT_TEMPLATE, but was: " + artifactType);
        }

        StoredArtifactVersionDto storedArtifact = storage.getArtifactVersionContent(
                gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

        // Variables bean uses additionalProperties for dynamic keys
        Map<String, Object> variables = request.getVariables().getAdditionalProperties();

        return promptRenderingService.render(
                storedArtifact.getContent(),
                variables,
                gav.getRawGroupIdWithNull() != null ? gav.getRawGroupIdWithNull() : "default",
                gav.getRawArtifactId(),
                gav.getRawVersionId());
    }
}
