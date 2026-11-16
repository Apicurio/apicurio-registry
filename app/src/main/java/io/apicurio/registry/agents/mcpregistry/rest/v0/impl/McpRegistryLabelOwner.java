package io.apicurio.registry.agents.mcpregistry.rest.v0.impl;

import io.apicurio.registry.extensions.FeatureLabelOwner;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * The MCP Registry API stores each published version's id and its status in labels.
 */
@ApplicationScoped
public class McpRegistryLabelOwner implements FeatureLabelOwner {

    @Override
    public Set<String> ownedLabelPrefixes() {
        return Set.of("apicurio.mcp-registry.");
    }

    @Override
    public Set<String> ownedLabelKeys() {
        return Set.of(McpRegistryApiResourceImpl.LEGACY_SERVER_VERSION_ID_LABEL);
    }
}
