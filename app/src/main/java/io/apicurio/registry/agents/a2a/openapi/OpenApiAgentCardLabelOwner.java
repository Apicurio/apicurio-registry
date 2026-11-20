package io.apicurio.registry.agents.a2a.openapi;

import io.apicurio.registry.agents.a2a.A2AConstants;
import io.apicurio.registry.extensions.FeatureLabelOwner;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Agent Cards generated from OpenAPI artifacts record their source and content hash in labels,
 * and the source records its generated card.
 */
@ApplicationScoped
public class OpenApiAgentCardLabelOwner implements FeatureLabelOwner {

    @Override
    public Set<String> ownedLabelPrefixes() {
        return Set.of(A2AConstants.PREFIX_OPENAPI_AGENT_CARD);
    }
}
