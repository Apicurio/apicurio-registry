package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.rest.v3.beans.AgentInventorySource;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncRequest;

import java.util.List;
import java.util.Map;

/**
 * Lists the agents or MCP servers of one inventory source. Implementations only read: the source
 * stays the source of truth for its items, and the registry never writes back.
 */
public interface InventorySource {

    /**
     * @return the source this implementation reads
     */
    AgentInventorySource source();

    /**
     * Checks the request for this source and returns the scope it reads, such as a namespace.
     * Called before anything is read or written.
     *
     * @param request the sync request
     * @return the scope, recorded on each artifact so that a later sync of the same scope finds it
     * @throws jakarta.ws.rs.BadRequestException when the request is not valid for this source
     */
    String scope(AgentInventorySyncRequest request);

    /**
     * @param scope    the scope returned by {@link #scope}
     * @param headers  validated headers to send to the source, possibly empty
     * @param maxItems the most items to accept; listing more fails the sync
     * @return the items, each with a distinct external ID
     * @throws InventorySourceException when the source cannot be read
     */
    List<InventoryItem> list(String scope, Map<String, String> headers, int maxItems);
}
