package io.apicurio.registry.agents.inventory;

/**
 * One agent or MCP server listed by an inventory source.
 *
 * @param externalId   the item's ID in the source, unique within the source and scope
 * @param artifactId   the ID of the artifact that holds it, derived from the external ID
 * @param artifactType {@code MCP_SERVER} or {@code AGENT_CARD}
 * @param name         a display name for the artifact
 * @param description  a description for the artifact, or {@code null}
 * @param content      the artifact content: an MCP server.json document or an A2A Agent Card
 * @param mcpUrl       for MCP servers, the Streamable HTTP endpoint to import tools from, or
 *                     {@code null} when the server has none yet
 */
public record InventoryItem(String externalId, String artifactId, String artifactType, String name,
        String description, String content, String mcpUrl) {
}
