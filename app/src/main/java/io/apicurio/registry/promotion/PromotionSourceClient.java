package io.apicurio.registry.promotion;

/**
 * Fetches an artifact version from a promotion source.
 */
public interface PromotionSourceClient {

    RemoteArtifactVersion fetch(String groupId, String artifactId, String versionExpression);
}
