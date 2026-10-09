package io.apicurio.registry.promotion;

/**
 * Named source registry used for cross-environment promotion. Secrets are held in memory only and must
 * never be returned from REST APIs.
 */
public record PromotionSourceDefinition(String name, String url, String auth, String token, String username,
        String password, String tokenUrl, String clientId, String clientSecret) {

    public boolean isLocal() {
        String value = url == null ? "" : url.trim();
        return value.startsWith("local:");
    }
}
