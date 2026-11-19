package io.apicurio.registry.extensions;

import io.apicurio.registry.rest.v3.beans.AgentInventorySyncRequest;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncResult;

/**
 * Extension point implementing the v3 {@code /groups/{groupId}/agent-inventory-sync} endpoint.
 * <p>
 * The endpoint is part of the core REST API, but the behaviour belongs to the optional agents
 * module. When no bean implements this interface the endpoint responds with 409, as when the
 * feature is disabled.
 * </p>
 */
public interface AgentInventorySyncHandler {

    /**
     * Reads the items of the inventory source in the request and keeps one artifact per item in the
     * given group. Write access to the group has already been checked by the caller.
     *
     * @param groupId the group id as given in the request
     * @param request the sync request
     * @return the result for each item
     */
    AgentInventorySyncResult sync(String groupId, AgentInventorySyncRequest request);
}
