package io.apicurio.registry.rest.wellknown;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v3.WellResource;
import io.apicurio.registry.rest.v3.beans.AgentCard;
import io.apicurio.registry.rest.v3.beans.AgentSearchRequest;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResults;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.Response;

import java.math.BigInteger;
import java.util.List;

/**
 * Exposes well-known endpoints under the {@code /apis/registry/v3/well-known} path so that SDK
 * clients (Go, Java, Python, TypeScript) generated from the OpenAPI spec can reach them using
 * the standard v3 base URL.
 *
 * <p>All calls delegate to {@link WellKnownResourceImpl}, which also serves the canonical
 * {@code /.well-known/} paths for A2A protocol compliance.
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
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
    public AgentSearchResults searchAgents(String name, List<String> skill,
            List<String> capability, List<String> inputMode, List<String> outputMode,
            BigInteger offset, BigInteger limit) {
        return delegate.searchAgents(name, skill, capability, inputMode, outputMode,
                offset.intValue(), limit.intValue());
    }

    @Override
    public AgentSearchResults searchPublicAgents(BigInteger offset, BigInteger limit) {
        return delegate.getPublicAgents(offset.intValue(), limit.intValue());
    }

    @Override
    public AgentSearchResults searchEntitledAgents(BigInteger offset, BigInteger limit) {
        return delegate.getEntitledAgents(offset.intValue(), limit.intValue());
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
    public McpToolSearchResults searchMcpTools(String name, List<String> parameter,
            BigInteger offset, BigInteger limit) {
        return delegate.searchMcpTools(name, parameter, offset.intValue(), limit.intValue());
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
