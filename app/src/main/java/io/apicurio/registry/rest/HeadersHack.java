package io.apicurio.registry.rest;

import java.util.function.Supplier;

import jakarta.ws.rs.core.Response;

import io.apicurio.registry.types.ArtifactState;

/**
 * Remove once Quarkus issue #9887 is fixed!
 *
 */
public class HeadersHack {
    public static void checkIfDeprecated(
            Supplier<ArtifactState> stateSupplier,
            String groupId,
            String artifactId,
            Object version,
            Response.ResponseBuilder builder
    ) {
        if (stateSupplier.get() == ArtifactState.DEPRECATED) {
            builder.header(Headers.DEPRECATED, true);
            builder.header(Headers.GROUP_ID, groupId);
            builder.header(Headers.ARTIFACT_ID, artifactId);
            builder.header(Headers.VERSION, version != null ? String.valueOf(version) : null);
        }
    }
}
