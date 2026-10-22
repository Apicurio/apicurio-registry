package io.apicurio.registry.content.extract;

/**
 * Performs metadata extraction for A2A Agent Card content.
 *
 * Extracts the agent name and description from the Agent Card JSON structure.
 * Delegates to {@link JsonNameDescriptionContentExtractor} for the actual extraction.
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
 */
public class AgentCardContentExtractor extends JsonNameDescriptionContentExtractor {
}
