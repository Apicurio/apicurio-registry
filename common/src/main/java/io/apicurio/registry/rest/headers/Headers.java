package io.apicurio.registry.rest.headers;

import io.apicurio.registry.types.VersionState;
import jakarta.ws.rs.core.Response;

import java.util.function.Supplier;

public final class Headers {

    public static final String GROUP_ID = "X-Registry-GroupId";
    public static final String ARTIFACT_ID = "X-Registry-ArtifactId";
    public static final String VERSION = "X-Registry-Version";
    public static final String ARTIFACT_TYPE = "X-Registry-ArtifactType";
    public static final String HASH_ALGO = "X-Registry-Hash-Algorithm";
    public static final String ARTIFACT_HASH = "X-Registry-Content-Hash";
    public static final String DEPRECATED = "X-Registry-Deprecated";
    public static final String NAME = "X-Registry-Name";
    public static final String NAME_ENCODED = "X-Registry-Name-Encoded";
    public static final String DESCRIPTION = "X-Registry-Description";
    public static final String DESCRIPTION_ENCODED = "X-Registry-Description-Encoded";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String PRESERVE_GLOBAL_ID = "X-Registry-Preserve-GlobalId";
    public static final String PRESERVE_CONTENT_ID = "X-Registry-Preserve-ContentId";

    /**
     * Remove once Quarkus issue #9887 is fixed!
     * <p>
     * TODO: Is this the correct issue https://github.com/quarkusio/quarkus/issues/9887 ?
     */
    public static void checkIfDeprecated(Supplier<VersionState> stateSupplier, String groupId,
                                         String artifactId, String version, Response.ResponseBuilder builder) {
        if (stateSupplier.get() == VersionState.DEPRECATED) {
            builder.header(Headers.DEPRECATED, true);
            builder.header(Headers.GROUP_ID, groupId);
            builder.header(Headers.ARTIFACT_ID, artifactId);
            builder.header(Headers.VERSION, version);
        }
    }

    private Headers() {
    }
}
