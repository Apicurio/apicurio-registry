package io.apicurio.registry.cli.auth;

/**
 * Token endpoint response containing access and refresh tokens.
 */
public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
