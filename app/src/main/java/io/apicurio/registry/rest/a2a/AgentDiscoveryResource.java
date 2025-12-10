package io.apicurio.registry.rest.a2a;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.rest.a2a.beans.AgentCard;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.types.ArtifactType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Discovery endpoint for A2A Agent Cards following the .well-known convention.
 *
 * This endpoint returns a catalog of all registered agents in the registry,
 * enabling agent discovery for A2A protocol implementations.
 */
@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
@Path("/.well-known")
public class AgentDiscoveryResource {

    private static final String AGENTS_GROUP_ID = "agents";

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Returns a catalog of all registered A2A Agent Cards for discovery purposes.
     */
    @GET
    @Path("/agents.json")
    @Produces(MediaType.APPLICATION_JSON)
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AgentCatalog getAgentCatalog() {
        Set<SearchFilter> filters = Set.of(
                SearchFilter.ofGroupId(AGENTS_GROUP_ID),
                SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD));

        ArtifactSearchResultsDto searchResults = storage.searchArtifacts(filters, OrderBy.name,
                OrderDirection.asc, 0, 1000);

        List<AgentCard> agents = new ArrayList<>();

        for (SearchedArtifactDto artifact : searchResults.getArtifacts()) {
            try {
                // Get latest version using branch tip
                GAV latestGAV = storage.getBranchTip(new GA(AGENTS_GROUP_ID, artifact.getArtifactId()),
                        BranchId.LATEST, RetrievalBehavior.SKIP_DISABLED_LATEST);
                StoredArtifactVersionDto content = storage.getArtifactVersionContent(AGENTS_GROUP_ID,
                        artifact.getArtifactId(), latestGAV.getRawVersionId());

                AgentCard agentCard = objectMapper.readValue(content.getContent().content(), AgentCard.class);
                agents.add(agentCard);
            } catch (Exception e) {
                log.warn("Failed to parse agent card for artifact {}: {}", artifact.getArtifactId(),
                        e.getMessage());
            }
        }

        return AgentCatalog.builder().version("1.0").generatedAt(Instant.now().toString())
                .count(agents.size()).agents(agents).build();
    }

    /**
     * Catalog of available A2A agents.
     */
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Getter
    @lombok.Setter
    public static class AgentCatalog {
        private String version;
        private String generatedAt;
        private int count;
        private List<AgentCard> agents;
    }
}
