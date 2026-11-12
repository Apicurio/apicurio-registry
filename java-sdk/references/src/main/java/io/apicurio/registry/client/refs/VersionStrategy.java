package io.apicurio.registry.client.refs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import io.apicurio.registry.content.refs.resolve.PlannedArtifact;
import io.apicurio.registry.content.refs.resolve.RootArtifact;
import io.apicurio.registry.types.ArtifactType;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * Decides the version to register a root artifact under.
 */
@FunctionalInterface
public interface VersionStrategy {

    /**
     * Resolves the version for a root artifact.
     *
     * @param artifact the root artifact being registered
     * @param content the content that will be sent
     * @return the resolved version
     */
    ResolvedVersion resolve(PlannedArtifact artifact, String content);

    /**
     * Uses only the version and draft flag declared on the {@link RootArtifact}.
     *
     * @return the strategy
     */
    static VersionStrategy explicitOnly() {
        return (artifact, content) -> {
            RootArtifact root = artifact.root();
            if (root == null) {
                return new ResolvedVersion(null, null, false, false);
            }
            String version = root.version() == null || root.version().isBlank() ? null : root.version();
            return new ResolvedVersion(version, root.draft(), false, false);
        };
    }

    /**
     * Uses the explicit root version when present; otherwise, for OpenAPI and AsyncAPI documents, uses
     * {@code info.version}. A {@code -SNAPSHOT} suffix is stripped and the version is registered as a draft.
     *
     * @return the strategy
     */
    static VersionStrategy apiInfoVersion() {
        return (artifact, content) -> {
            ResolvedVersion explicit = explicitOnly().resolve(artifact, content);
            if (explicit.version() != null || !(ArtifactType.OPENAPI.equals(artifact.artifactType())
                    || ArtifactType.ASYNCAPI.equals(artifact.artifactType()))) {
                return explicit;
            }
            String infoVersion = ApiInfo.version(content, artifact.contentType());
            if (infoVersion == null) {
                return explicit;
            }
            if (infoVersion.endsWith(ApiInfo.SNAPSHOT)) {
                return new ResolvedVersion(infoVersion.substring(0, infoVersion.length() - ApiInfo.SNAPSHOT.length()),
                        Boolean.TRUE, true, true);
            }
            return new ResolvedVersion(infoVersion, explicit.draft(), true, false);
        };
    }

    /**
     * Helper for reading {@code info.version}.
     */
    final class ApiInfo {

        static final String SNAPSHOT = "-SNAPSHOT";

        private ApiInfo() {
        }

        static String version(String content, String contentType) {
            boolean yaml = contentType != null && contentType.toLowerCase(Locale.ROOT).contains("yaml");
            ObjectMapper mapper = yaml ? new YAMLMapper() : new JsonMapper();
            try {
                JsonNode node = mapper.readTree(content).path("info").path("version");
                return node.isString() && !node.asString().isBlank() ? node.asString().trim() : null;
            } catch (JacksonException e) {
                throw new UncheckedIOException("Unable to read info.version", new IOException(e.getMessage(), e));
            }
        }
    }
}
