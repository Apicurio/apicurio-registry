package io.apicurio.registry.client.common;

/**
 * Defines the HTTP adapter type to use for registry client communication.
 * This allows users to choose between different HTTP client implementations
 * based on their requirements and environment constraints.
 */
public enum HttpAdapterType {

    /**
     * Use Vert.x WebClient for HTTP communication.
     * This is the full-featured option with built-in OAuth2 support via vertx-auth-oauth2.
     * Recommended for high-throughput applications and when Vert.x is already in use.
     * Requires: kiota-http-vertx, vertx-auth-oauth2
     */
    VERTX,

    /**
     * Use JDK 11+ HttpClient for HTTP communication.
     * This is a lightweight option with minimal dependencies.
     * Better for simple applications or environments with classpath constraints
     * (e.g., GraalVM native images, certain classloader scenarios).
     * Requires: kiota-http-jdk
     */
    JDK,

    /**
     * Auto-detect the available adapter at runtime.
     * Prefers Vert.x if available (for backward compatibility), falls back to JDK adapter.
     * This is the default behavior.
     */
    AUTO
}
