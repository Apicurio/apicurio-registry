package io.apicurio.registry.rest.wellknown;

import io.apicurio.registry.rest.v3.WellResource;
import io.apicurio.registry.rest.v3.beans.AgentCard;
import io.apicurio.registry.rest.v3.beans.AgentSearchRequest;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResults;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.math.BigInteger;
import java.util.List;

/**
 * Thin delegate that serves well-known endpoints under {@code /apis/registry/v3/well-known}
 * so SDK clients can reach them using the standard v3 base URL. All calls forward to
 * {@link WellKnownResourceImpl} which also serves the canonical {@code /.well-known/} paths.
 */
@ApplicationScoped
public class V3WellKnownResource implements WellResource {

    @Inject
    WellKnownResourceImpl delegate;

    @Override
    public AgentCard getAgentCard() {
        return delegate.getAgentCard();
    }

    @Override
    public AgentCard getAgentCardV1() {
        return delegate.getAgentCardV1();
    }

    @Override
    public AgentCard getAgentCardForOrchestrate() {
        return delegate.getAgentCardForOrchestrate();
    }

    @Override
    public AgentSearchResults searchAgents(BigInteger offset, BigInteger limit,
            String name, List<String> skill, List<String> capability,
            List<String> inputMode, List<String> outputMode) {
        int off = offset != null ? offset.intValue() : 0;
        int lim = limit != null ? limit.intValue() : 20;
        return delegate.searchAgents(name, skill, capability, inputMode, outputMode, off, lim);
    }

    @Override
    public AgentSearchResults searchPublicAgents(BigInteger offset, BigInteger limit) {
        int off = offset != null ? offset.intValue() : 0;
        int lim = limit != null ? limit.intValue() : 20;
        return delegate.getPublicAgents(off, lim);
    }

    @Override
    public AgentSearchResults searchEntitledAgents(BigInteger offset, BigInteger limit) {
        int off = offset != null ? offset.intValue() : 0;
        int lim = limit != null ? limit.intValue() : 20;
        return delegate.getEntitledAgents(off, lim);
    }

    @Override
    public AgentSearchResults searchAgentsAdvanced(AgentSearchRequest data) {
        return delegate.searchAgentsAdvanced(data);
    }

    @Override
    public Response getRegisteredAgentCard(String groupId, String artifactId, String version) {
        return delegate.getRegisteredAgentCard(groupId, artifactId, version);
    }

    @Override
    public McpToolSearchResults searchMcpTools(BigInteger offset, BigInteger limit,
            String name, List<String> parameter) {
        String off = offset != null ? offset.toString() : "0";
        String lim = limit != null ? limit.toString() : "20";
        return delegate.searchMcpTools(name, parameter, off, lim);
    }

    @Override
    public Response getRegisteredMcpTool(String groupId, String artifactId, String version) {
        return delegate.getRegisteredMcpTool(groupId, artifactId, version);
    }

    @Override
    public Response getSchema(String schemaType, String version) {
        return delegate.getSchema(schemaType, version);
    }
}
