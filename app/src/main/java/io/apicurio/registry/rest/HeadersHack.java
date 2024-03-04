package io.apicurio.registry.rest;

import java.util.function.Supplier;

import io.apicurio.registry.types.VersionState;
import jakarta.ws.rs.core.Response;

/**
 * Remove once Quarkus issue #9887 is fixed!
 *
 */
public class HeadersHack {
    public static void checkIfDeprecated(
            Supplier<VersionState> stateSupplier,
            String groupId,
            String artifactId,
            Object version,
            Response.ResponseBuilder builder
    ) {
        if (stateSupplier.get() == VersionState.DEPRECATED) {
            builder.header(Headers.DEPRECATED, true);
            builder.header(Headers.GROUP_ID, groupId);
            builder.header(Headers.ARTIFACT_ID, artifactId);
            builder.header(Headers.VERSION, version != null ? String.valueOf(version) : null);
        }
    }
}
