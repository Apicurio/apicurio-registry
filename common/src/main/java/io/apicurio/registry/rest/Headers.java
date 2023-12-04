package io.apicurio.registry.rest;

import io.apicurio.registry.types.ArtifactState;

import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;


public interface Headers {
    String GROUP_ID = "X-Registry-GroupId";
    String ARTIFACT_ID = "X-Registry-ArtifactId";
    String VERSION = "X-Registry-Version";
    String ARTIFACT_TYPE = "X-Registry-ArtifactType";
    String HASH_ALGO = "X-Registry-Hash-Algorithm";
    String ARTIFACT_HASH = "X-Registry-Content-Hash";
    String DEPRECATED = "X-Registry-Deprecated";
    String NAME = "X-Registry-Name";
    String NAME_ENCODED = "X-Registry-Name-Encoded";
    String DESCRIPTION = "X-Registry-Description";
    String DESCRIPTION_ENCODED = "X-Registry-Description-Encoded";
    String CONTENT_TYPE = "Content-Type";
    String PRESERVE_GLOBAL_ID = "X-Registry-Preserve-GlobalId";
    String PRESERVE_CONTENT_ID = "X-Registry-Preserve-ContentId";

    default void checkIfDeprecated(
        Supplier<ArtifactState> stateSupplier,
        String groupId,
        String artifactId,
        Number version,
        Response.ResponseBuilder builder
    ) {
        if (stateSupplier.get() == ArtifactState.DEPRECATED) {
            builder.header(Headers.DEPRECATED, true);
            builder.header(Headers.GROUP_ID, groupId);
            builder.header(Headers.ARTIFACT_ID, artifactId);
            builder.header(Headers.VERSION, version);
        }
    }
}
