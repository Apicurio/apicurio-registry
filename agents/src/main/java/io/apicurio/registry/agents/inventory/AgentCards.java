package io.apicurio.registry.agents.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Brings an Agent Card read from an inventory source to the A2A 1.0 shape the registry stores.
 * <p>
 * Only the structure changes, never the claims: an A2A 0.3 card's {@code url}, with its
 * {@code preferredTransport} and {@code protocolVersion}, becomes the one entry of
 * {@code supportedInterfaces}. Nothing the source did not state is added. A card that lacks
 * something A2A 1.0 requires, such as skills, is stored as read, and the artifact's rules decide
 * whether it is accepted.
 */
final class AgentCards {

    // The transport an A2A 0.3 card uses when it names none.
    private static final String DEFAULT_PROTOCOL_BINDING = "JSONRPC";
    // An A2A 0.3 card's interface speaks that protocol version when the card does not say.
    private static final String DEFAULT_PROTOCOL_VERSION = "0.3.0";

    private AgentCards() {
    }

    /**
     * @param card the card as read, which is not changed
     * @return a copy in the A2A 1.0 shape
     */
    static ObjectNode normalize(JsonNode card) {
        ObjectNode normalized = card.deepCopy();
        if (!normalized.has("supportedInterfaces") && normalized.path("url").isTextual()) {
            ObjectNode iface = normalized.putArray("supportedInterfaces").addObject();
            iface.put("url", normalized.get("url").asText());
            iface.put("protocolBinding", normalized.path("preferredTransport").isTextual()
                    ? normalized.get("preferredTransport").asText() : DEFAULT_PROTOCOL_BINDING);
            iface.put("protocolVersion", normalized.path("protocolVersion").isTextual()
                    ? normalized.get("protocolVersion").asText() : DEFAULT_PROTOCOL_VERSION);
        }
        return normalized;
    }
}
