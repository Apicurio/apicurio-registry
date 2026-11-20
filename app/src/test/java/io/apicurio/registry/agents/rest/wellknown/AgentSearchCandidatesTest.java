package io.apicurio.registry.agents.rest.wellknown;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.agents.noprofile.rest.aicatalog.AiCatalogEnabledProfile;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.registry.storage.RegistryStorage;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Searching agents must not collect MCP tools, and must read each matching card's content once:
 * every read is a storage round trip, and repeating them made skill searches slow on PostgreSQL.
 */
@QuarkusTest
@TestProfile(AiCatalogEnabledProfile.class)
public class AgentSearchCandidatesTest extends AbstractResourceTestBase {

    private static final String AGENT_CARD = """
            {"name": "Skilled Agent", "description": "An agent", "version": "1.0.0",
             "supportedInterfaces": [{"url": "https://example.com/agent", "protocolBinding": "JSONRPC",
               "protocolVersion": "1.0"}],
             "capabilities": {}, "skills": [{"id": "candidates-skill", "name": "S", "description": "S",
               "tags": ["t"]}], "defaultInputModes": ["text"], "defaultOutputModes": ["text"]}
            """;
    private static final String MCP_TOOL = """
            {"name": "lookup", "inputSchema": {"type": "object", "properties": {"q": {"type": "string"}}}}
            """;

    @InjectSpy
    AiCatalogDiscovery aiCatalogDiscovery;

    @Inject
    AgentCardDiscovery agentCardDiscovery;

    @Test
    void skillSearchCollectsOnlyAgents() throws Exception {
        String group = TestUtils.generateGroupId();
        createArtifact(group, "skilled", ArtifactType.AGENT_CARD, AGENT_CARD, ContentTypes.APPLICATION_JSON);
        createArtifact(group, "tool", ArtifactType.MCP_TOOL, MCP_TOOL, ContentTypes.APPLICATION_JSON);

        // Count the storage reads both discovery beans make during the search, whichever code path
        // makes them; artifact setup above is not counted.
        AiCatalogDiscovery catalog = ClientProxy.unwrap(aiCatalogDiscovery);
        AgentCardDiscovery cards = ClientProxy.unwrap(agentCardDiscovery);
        RegistryStorage original = catalog.storage;
        RegistryStorage counting = Mockito.spy(original);
        catalog.storage = counting;
        cards.storage = counting;
        try {
            search();
        } finally {
            catalog.storage = original;
            cards.storage = original;
        }

        verify(counting, times(1)).getArtifactVersionContent(eq(group), eq("skilled"), anyString());
        verify(counting, times(1)).getBranchTip(argThat(ga -> "skilled".equals(ga.getRawArtifactId())), any(), any());
        verify(aiCatalogDiscovery, times(1)).collectAgentCandidates(anyString(), any(), any(), any());
        verify(aiCatalogDiscovery, never()).collectAiCatalogCandidates(any(), any(), any(), any());
    }

    private void search() {
        given().baseUri("http://localhost:" + ConfigProvider.getConfig().getValue("quarkus.http.test-port",
                Integer.class)).basePath("").queryParam("skill", "candidates-skill")
                .get("/.well-known/agents").then().statusCode(200)
                .body("count", equalTo(1))
                .body("agents.artifactId", contains("skilled"));
    }
}
