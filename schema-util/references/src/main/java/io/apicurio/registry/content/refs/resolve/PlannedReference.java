package io.apicurio.registry.content.refs.resolve;

/**
 * A reference from a planned artifact to its target.
 *
 * @param name the reference name (the full reference string as it appears in the referencing schema)
 * @param target the target of the reference
 */
public record PlannedReference(String name, ReferenceTarget target) {
}
