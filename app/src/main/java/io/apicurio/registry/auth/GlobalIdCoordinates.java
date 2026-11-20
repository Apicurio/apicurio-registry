package io.apicurio.registry.auth;

/**
 * A global ID's coordinates, exactly as {@link io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto}
 * stores them (no normalization): the artifact a global ID addresses, used to authorize requests by
 * global ID. Resolved per request (see {@link AuthorizationLookups#coordinates}); never cache it across
 * requests, since a data import that preserves global IDs can rebind an ID to a different artifact.
 */
public record GlobalIdCoordinates(String groupId, String artifactId, String version) {
}
