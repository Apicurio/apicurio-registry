package io.apicurio.registry.content.refs.resolve;

/**
 * A reference that could not be resolved to a local source or existing artifact.
 *
 * @param fromLocation the location of the referencing source
 * @param reference the full reference string
 */
public record UnresolvedReference(String fromLocation, String reference) {
}
