package io.apicurio.registry.extensions;

import io.apicurio.registry.rest.v3.beans.AgentSearchResults;

import java.util.List;

/**
 * Extension point through which the core REST API searches the agent cards of this registry.
 * <p>
 * The federated agent search endpoint is part of the core REST API and the peers it fans out to are
 * core data, but searching this registry's own agent cards belongs to the optional agents module.
 * When no bean implements this interface the endpoint responds with 404.
 * </p>
 */
public interface AgentSearchHandler {

    /**
     * Searches the agent cards of this registry that the current caller may see.
     *
     * @param name         filter by agent name (partial match), or null
     * @param skills       skill ids every result must have, or null
     * @param capabilities capabilities every result must have, or null
     * @param inputModes   default input modes every result must have, or null
     * @param outputModes  default output modes every result must have, or null
     * @param offset       number of matches to skip
     * @param limit        maximum number of matches to return
     * @param publicOnly   when true, only cards whose effective visibility is public, whoever the caller is
     * @param checkpoint   run between the units of work of the search, so that a caller with a deadline
     *                     can stop the search by throwing from it
     * @return the matching agent cards
     */
    AgentSearchResults searchAgents(String name, List<String> skills, List<String> capabilities,
            List<String> inputModes, List<String> outputModes, Integer offset, Integer limit,
            Boolean publicOnly, Runnable checkpoint);
}
