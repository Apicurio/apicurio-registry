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
     * Prefix for ARD {@code urn:air:<publisher>:<group>:<artifact>} identifiers.
     */
    public static final String URN_AIR_PREFIX = "urn:air:";
}
