package io.apicurio.registry.content.extract;

/**
 * Represents a structured element extracted from artifact content for search indexing. The kind identifies the
 * type of element (e.g., "schema", "path", "message", "field") and the name is the element's identifier.
 */
public record StructuredElement(String kind, String name) {
}
