package io.apicurio.registry.references.auto;

/**
 * A reference that could not be matched to exactly one existing artifact.
 *
 * @param reference the full reference as written in the content
 * @param description a human-readable description of the problem
 */
public record MatchProblem(String reference, String description) {
}
