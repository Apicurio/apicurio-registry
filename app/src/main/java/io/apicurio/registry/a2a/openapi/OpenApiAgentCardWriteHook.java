package io.apicurio.registry.a2a.openapi;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.extensions.ArtifactVersionWriteHook;
import io.apicurio.registry.extensions.VersionWriteContext;
import io.apicurio.registry.types.ArtifactType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Generates or synchronizes the AGENT_CARD companion of an OPENAPI artifact that carries an
 * {@code x-agent-card} extension. A malformed extension rejects the write before anything is persisted;
 * the companion itself is written only after the OpenAPI version was published.
 */
@ApplicationScoped
public class OpenApiAgentCardWriteHook implements ArtifactVersionWriteHook {

    @Inject
    OpenApiAgentCardService openApiAgentCardService;

    @Override
    public Runnable beforePublish(VersionWriteContext context, TypedContent content) {
        if (!ArtifactType.OPENAPI.equals(context.getArtifactType())) {
            return null;
        }
        String cardJson = openApiAgentCardService.validateAndAssemble(content, context.isUpdate());
        if (cardJson == null) {
            return null;
        }
        return () -> openApiAgentCardService.createOrSyncCompanion(context.getStorage(),
                context.getGa().getRawGroupIdWithNull(), context.getGa().getRawArtifactId(), cardJson,
                context.getOwner());
    }
}
