package io.apicurio.registry.agents.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.model.ArtifactId;
import io.apicurio.registry.rest.v3.beans.AgentInventorySource;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncRequest;
import io.apicurio.registry.types.ArtifactType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Agents registered in Google Cloud Agent Registry (API v1), as {@code AGENT_CARD} artifacts. An
 * agent's stored A2A Agent Card is used as is when the registry has one; otherwise the card is
 * built from the agent's display name, description, version, skills and A2A interfaces.
 * <p>
 * The caller sends its own credentials in the request headers, usually an OAuth 2.0 bearer token
 * with the {@code agentregistry.read-only} scope; the registry stores none.
 */
@ApplicationScoped
public class GoogleAgentRegistrySource implements InventorySource {

    static final String ARTIFACT_ID_PREFIX = "gcp-agent";

    // Project IDs and location names, as Google Cloud allows them.
    private static final Pattern LOCATION = Pattern.compile("projects/([a-z][a-z0-9-]{4,28}[a-z0-9])/locations/([a-z0-9-]{1,63})");
    private static final String A2A_AGENT_CARD = "A2A_AGENT_CARD";
    private static final String A2A_AGENT = "A2A_AGENT";
    private static final int PAGE_SIZE = 100;
    private static final long MAX_RESPONSE_BYTES = 10L * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    AgentInventoryConfig config;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public AgentInventorySource source() {
        return AgentInventorySource.GOOGLE_AGENT_REGISTRY;
    }

    @Override
    public String scope(AgentInventorySyncRequest request) {
        String location = request.getLocation();
        if (location == null || !LOCATION.matcher(location).matches()) {
            throw new BadRequestException(
                    "A location is required for Google Cloud Agent Registry, as projects/{project}/locations/{location}.");
        }
        if (request.getNamespace() != null) {
            throw new BadRequestException("A namespace is only used by Kubernetes inventory sources.");
        }
        return location;
    }

    @Override
    public List<InventoryItem> list(String location, Map<String, String> headers, int maxItems) {
        List<InventoryItem> items = new ArrayList<>();
        Set<String> seenTokens = new HashSet<>();
        String pageToken = null;
        do {
            JsonNode page = get(location, pageToken, headers);
            for (JsonNode agent : page.path("agents")) {
                if (items.size() == maxItems) {
                    throw new InventorySourceException("The location has more agents than one sync accepts.");
                }
                items.add(item(location, agent));
            }
            pageToken = page.path("nextPageToken").isTextual() && !page.get("nextPageToken").asText().isEmpty()
                    ? page.get("nextPageToken").asText() : null;
            if (pageToken != null && !seenTokens.add(pageToken)) {
                throw new InventorySourceException("Google Cloud Agent Registry's agent list does not end.");
            }
        } while (pageToken != null);
        return items;
    }

    private JsonNode get(String location, String pageToken, Map<String, String> headers) {
        String query = "pageSize=" + PAGE_SIZE
                + (pageToken != null ? "&pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8) : "");
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create(config.getGoogleUrl() + "/v1/" + location + "/agents?" + query))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds())).header("Accept", "application/json").GET();
        headers.forEach(request::header);
        HttpResponse<InputStream> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new InventorySourceException("Google Cloud Agent Registry could not be reached.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InventorySourceException("Reading Google Cloud Agent Registry was interrupted.");
        }
        try (InputStream body = response.body()) {
            switch (response.statusCode()) {
                case 200 -> {
                    byte[] bytes = body.readNBytes((int) MAX_RESPONSE_BYTES + 1);
                    if (bytes.length > MAX_RESPONSE_BYTES) {
                        throw new InventorySourceException("A response from Google Cloud Agent Registry was too large.");
                    }
                    JsonNode page = MAPPER.readTree(bytes);
                    if (page == null || !page.isObject()) {
                        throw new InventorySourceException("Google Cloud Agent Registry sent an invalid response.");
                    }
                    return page;
                }
                case 401, 403 -> throw new InventorySourceException(
                        "Google Cloud Agent Registry refused the credentials in the request.", 400);
                case 404 -> throw new InventorySourceException(
                        "Google Cloud Agent Registry does not know this location.", 400);
                default -> throw new InventorySourceException("Google Cloud Agent Registry could not list the agents.");
            }
        } catch (IOException e) {
            throw new InventorySourceException("Google Cloud Agent Registry sent an invalid response.");
        }
    }

    static InventoryItem item(String location, JsonNode agent) {
        String resourceName = agent.path("name").asText("");
        String agentId = resourceName.substring(resourceName.lastIndexOf('/') + 1);
        var matcher = LOCATION.matcher(location);
        if (!matcher.matches() || agentId.isEmpty() || !resourceName.equals(location + "/agents/" + agentId)) {
            throw new InventorySourceException("Google Cloud Agent Registry listed an agent outside the location.");
        }
        String artifactId = ARTIFACT_ID_PREFIX + "." + matcher.group(1) + "." + matcher.group(2) + "." + agentId;
        if (!ArtifactId.isValid(artifactId)) {
            throw new InventorySourceException("An agent ID could not be used as an artifact ID.");
        }
        ObjectNode card = card(agent);
        String name = card.path("name").isTextual() ? card.get("name").asText() : agentId;
        String description = card.path("description").isTextual() ? card.get("description").asText() : null;
        return new InventoryItem(resourceName, artifactId, ArtifactType.AGENT_CARD, name, description,
                card.toString(), null);
    }

    /** The agent's stored A2A card, or one built from the agent's own fields. */
    static ObjectNode card(JsonNode agent) {
        JsonNode stored = agent.path("card");
        if (A2A_AGENT_CARD.equals(stored.path("type").asText()) && stored.path("content").isObject()) {
            return AgentCards.normalize(stored.get("content"));
        }
        ObjectNode card = MAPPER.createObjectNode();
        copyText(agent, "displayName", card, "name");
        copyText(agent, "description", card, "description");
        copyText(agent, "version", card, "version");
        for (JsonNode protocol : agent.path("protocols")) {
            if (!A2A_AGENT.equals(protocol.path("type").asText())) {
                continue;
            }
            for (JsonNode iface : protocol.path("interfaces")) {
                ObjectNode entry = card.withArray("supportedInterfaces").addObject();
                copyText(iface, "url", entry, "url");
                copyText(iface, "protocolBinding", entry, "protocolBinding");
                copyText(protocol, "protocolVersion", entry, "protocolVersion");
            }
        }
        if (agent.path("skills").isArray()) {
            card.set("skills", agent.get("skills").deepCopy());
        }
        card.putObject("capabilities");
        return card;
    }

    private static void copyText(JsonNode from, String field, ObjectNode to, String as) {
        if (from.path(field).isTextual()) {
            to.put(as, from.get(field).asText());
        }
    }
}
