package io.apicurio.registry.auth;

import java.util.List;

/**
 * The registry resource addressed by an {@link Authorized} method invocation, resolved from its
 * {@link AuthorizedStyle} and parameters.
 *
 * @param kind what the invocation addresses
 * @param groupId raw group ID (null means the default group); set for GROUP and ARTIFACT
 * @param artifactId artifact ID; set for ARTIFACT
 * @param contentUsers for CONTENT: every artifact that uses the content (may be empty)
 */
public record AuthorizedResource(Kind kind, String groupId, String artifactId,
        List<AuthorizedResource> contentUsers) {

    public enum Kind {
        GROUP, ARTIFACT, CONTENT
    }

    public static AuthorizedResource group(String groupId) {
        return new AuthorizedResource(Kind.GROUP, groupId, null, List.of());
    }

    public static AuthorizedResource artifact(String groupId, String artifactId) {
        return new AuthorizedResource(Kind.ARTIFACT, groupId, artifactId, List.of());
    }

    public static AuthorizedResource content(List<AuthorizedResource> artifacts) {
        return new AuthorizedResource(Kind.CONTENT, null, null, List.copyOf(artifacts));
    }
}
