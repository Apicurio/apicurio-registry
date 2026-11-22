package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.extensions.FeatureLabelOwner;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Labels an inventory sync stores on the artifacts it creates, to find them again in the next sync
 * of the same source and scope. They identify where an artifact came from; they grant nothing.
 * Any caller with write access can change them, and an artifact whose labels no longer match is
 * treated as not managed by the sync, so the sync never changes it.
 */
@ApplicationScoped
public class AgentInventoryLabels implements FeatureLabelOwner {

    static final String PREFIX = "apicurio.agent-inventory.";

    /** The inventory source, for example {@code KUBERNETES_MCP_SERVERS}. */
    static final String SOURCE = PREFIX + "source";

    /** Where in the source the item was read: a namespace, or a Google Cloud location. */
    static final String SCOPE = PREFIX + "scope";

    /** The item's ID in the source. */
    static final String EXTERNAL_ID = PREFIX + "external-id";

    @Override
    public Set<String> ownedLabelPrefixes() {
        return Set.of(PREFIX);
    }
}
