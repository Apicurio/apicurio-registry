package io.apicurio.registry.agents.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * How each source's records become artifact content, without a registry.
 */
class InventoryMappingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LOCATION = "projects/demo-project/locations/us-central1";

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    @Test
    void a2a03CardGetsItsUrlAsTheSupportedInterface() throws Exception {
        JsonNode card = json("{\"name\":\"a\",\"url\":\"http://a:8000\",\"preferredTransport\":\"GRPC\","
                + "\"protocolVersion\":\"0.3.0\"}");

        JsonNode normalized = AgentCards.normalize(card);

        assertEquals("http://a:8000", normalized.at("/supportedInterfaces/0/url").asText());
        assertEquals("GRPC", normalized.at("/supportedInterfaces/0/protocolBinding").asText());
        assertEquals("0.3.0", normalized.at("/supportedInterfaces/0/protocolVersion").asText());
        assertFalse(card.has("supportedInterfaces"), "the card as read is not changed");
    }

    @Test
    void a2a10CardIsKeptAsIsAndNothingIsInvented() throws Exception {
        JsonNode card = json("{\"name\":\"a\",\"supportedInterfaces\":[{\"url\":\"https://a\","
                + "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"}]}");

        JsonNode normalized = AgentCards.normalize(card);

        assertEquals(card, normalized);
        assertFalse(normalized.has("skills"));
    }

    @Test
    void mcpServerWithoutAnAddressHasNoRemoteAndNoToolUrl() throws Exception {
        GenericKubernetesResource resource = new GenericKubernetesResource();
        resource.setMetadata(new ObjectMetaBuilder().withName("pending").build());

        InventoryItem item = KubernetesMcpServerSource.item("team-a", resource);

        assertEquals("team-a/pending", item.externalId());
        assertEquals("k8s-mcp.team-a.pending", item.artifactId());
        assertNull(item.mcpUrl());
        JsonNode server = json(item.content());
        assertEquals("io.k8s.team-a/pending", server.get("name").asText());
        assertEquals("0.0.0", server.get("version").asText());
        assertFalse(server.has("remotes"));
    }

    @Test
    void mcpServerDescriptionFitsTheServerJsonLimit() throws Exception {
        GenericKubernetesResource resource = new GenericKubernetesResource();
        String name = "a".repeat(63);
        String serverName = "b".repeat(60);
        resource.setMetadata(new ObjectMetaBuilder().withName(name).build());
        resource.setAdditionalProperty("status", Map.of("serverInfo", Map.of("name", serverName)));

        InventoryItem item = KubernetesMcpServerSource.item("team-a", resource);

        String full = "MCP server team-a/" + name + " (" + serverName + ")";
        assertEquals(full.substring(0, 100), json(item.content()).get("description").asText());
        assertEquals(full, item.description(), "the artifact's own description has no such limit");
    }

    @Test
    void googleAgentOutsideTheLocationIsRefused() throws Exception {
        JsonNode agent = json("{\"name\":\"projects/other/locations/us-central1/agents/x\"}");

        InventorySourceException e = assertThrows(InventorySourceException.class,
                () -> GoogleAgentRegistrySource.item(LOCATION, agent));
        assertEquals("Google Cloud Agent Registry listed an agent outside the location.", e.getMessage());
    }

    @Test
    void googleAgentWithoutAStoredCardIsBuiltFromItsFields() throws Exception {
        JsonNode agent = json("{\"name\":\"" + LOCATION + "/agents/travel\",\"displayName\":\"Travel\","
                + "\"protocols\":[{\"type\":\"CUSTOM\",\"interfaces\":[{\"url\":\"https://custom\"}]},"
                + "{\"type\":\"A2A_AGENT\",\"protocolVersion\":\"1.0\",\"interfaces\":[{\"url\":\"https://a2a\","
                + "\"protocolBinding\":\"HTTP_JSON\"}]}]}");

        JsonNode card = GoogleAgentRegistrySource.card(agent);

        assertEquals("Travel", card.get("name").asText());
        assertEquals(1, card.get("supportedInterfaces").size(), "only A2A interfaces are used");
        assertEquals("https://a2a", card.at("/supportedInterfaces/0/url").asText());
        assertEquals("HTTP_JSON", card.at("/supportedInterfaces/0/protocolBinding").asText());
        assertEquals("1.0", card.at("/supportedInterfaces/0/protocolVersion").asText());
    }
}
