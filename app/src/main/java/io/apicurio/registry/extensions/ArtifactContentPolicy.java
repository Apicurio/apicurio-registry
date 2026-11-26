package io.apicurio.registry.extensions;

import io.apicurio.registry.content.TypedContent;

/**
 * Extension point through which an optional module enforces a server-wide policy on the content of
 * artifact versions written through the APIs that do not run {@link ArtifactVersionWriteHook}: the v2
 * REST API and the Confluent-compatible API. The v3 REST API applies the same policies through its write
 * hooks, so implementations that must hold on every API implement both interfaces.
 * <p>
 * Implementations are CDI beans; every bean implementing this interface is invoked for every such write,
 * after rules and before the content is stored, so each implementation must ignore artifact types it does
 * not handle. Throwing rejects the write, just like a rule violation.
 * </p>
 */
public interface ArtifactContentPolicy {

    /**
     * @param artifactType the type of the artifact being written
     * @param content the content that is about to be stored
     */
    void check(String artifactType, TypedContent content);
}
