package io.apicurio.registry.cli.auth;

/**
 * OIDC provider endpoints discovered from .well-known/openid-configuration.
 */
record OidcEndpoints(String authorizationEndpoint, String tokenEndpoint) {
}
