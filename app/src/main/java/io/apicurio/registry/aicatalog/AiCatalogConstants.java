package io.apicurio.registry.aicatalog;

/**
 * Constants related to the AI Catalog (ai-catalog.io) and ARD (Agentic Resource Discovery)
 * well-known endpoints.
 */
public final class AiCatalogConstants {

    private AiCatalogConstants() {
        // utility class
    }

    /**
     * IANA media type reported for AI Catalog entries backed by an {@code AGENT_CARD} artifact.
     */
    public static final String MEDIA_TYPE_AGENT_CARD = "application/a2a-agent-card+json";

    /**
     * IANA media type reported for AI Catalog entries backed by an {@code MCP_TOOL} artifact.
     */
    public static final String MEDIA_TYPE_MCP_SERVER_CARD = "application/mcp-server-card+json";

    /**
     * IANA media type used for the self-describing entry that advertises this registry's own
     * ARD search API. Per the ARD specification (&sect;5.3), a conforming client discovers a
     * registry's search base URL by looking for a catalog entry whose {@code type} is this
     * value.
     */
    public static final String MEDIA_TYPE_AI_REGISTRY = "application/ai-registry+json";

    /**
     * Prefix for ARD {@code urn:air:<publisher>:<group>:<artifact>} identifiers.
     */
    public static final String URN_AIR_PREFIX = "urn:air:";

    /**
     * Suffix identifying the self-describing registry entry within its
     * {@code urn:air:<publisher>:<namespace>:<name>} identifier.
     */
    public static final String REGISTRY_SELF_ENTRY_NAME = "registry";
}
