package io.apicurio.registry.agents.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.v3.beans.AgentInventorySource;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncRequest;
import io.apicurio.registry.types.ArtifactType;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code AgentCard} resources of the Rossoctl operator (formerly Kagenti), as {@code AGENT_CARD}
 * artifacts. The operator fetches each agent's card and caches it in {@code status.card}; a
 * resource whose card has not been fetched yet is skipped until it has one.
 */
@ApplicationScoped
public class KubernetesAgentCardSource implements InventorySource {

    static final String KIND = "AgentCard";
    static final String PLURAL = "agentcards";
    static final List<String> VERSIONS = List.of("v1alpha1");
    static final String ARTIFACT_ID_PREFIX = "k8s-agent";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    AgentInventoryConfig config;

    @Inject
    Instance<KubernetesClient> client;

    @Override
    public AgentInventorySource source() {
        return AgentInventorySource.KUBERNETES_AGENT_CARDS;
    }

    @Override
    public String scope(AgentInventorySyncRequest request) {
        return KubernetesInventory.namespace(request, config);
    }

    @Override
    public List<InventoryItem> list(String namespace, Map<String, String> headers, int maxItems) {
        KubernetesInventory.requireNoHeaders(headers);
        List<InventoryItem> items = new ArrayList<>();
        for (GenericKubernetesResource resource : KubernetesInventory.list(client, config.getAgentCardGroup(),
                VERSIONS, KIND, PLURAL, namespace, maxItems)) {
            Map<String, Object> status = KubernetesInventory.map(resource.getAdditionalProperties().get("status"));
            Object card = status.get("card");
            if (card instanceof Map<?, ?>) {
                items.add(item(namespace, resource.getMetadata().getName(), MAPPER.valueToTree(card)));
            }
        }
        return items;
    }

    static InventoryItem item(String namespace, String name, JsonNode card) {
        JsonNode normalized = AgentCards.normalize(card);
        String cardName = normalized.path("name").isTextual() ? normalized.get("name").asText() : name;
        String description = normalized.path("description").isTextual() ? normalized.get("description").asText()
                : null;
        return new InventoryItem(namespace + "/" + name,
                KubernetesInventory.artifactId(ARTIFACT_ID_PREFIX, namespace, name), ArtifactType.AGENT_CARD,
                cardName, description, normalized.toString(), null);
    }
}
