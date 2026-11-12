package io.apicurio.registry.content.refs.resolve;

import tools.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apitomy.datamodels.Library;
import io.apitomy.datamodels.models.Document;
import io.apitomy.datamodels.util.ModelTypeUtil;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Detects the artifact type of a {@link SchemaSource} using, in order: the explicit type, the file
 * extension, structural inspection of JSON/YAML content, and finally each provider's content accepter.
 */
public final class TypeDetector {

    private static final Map<String, String> FALLBACK_EXTENSIONS = Map.of("avsc", ArtifactType.AVRO, "proto",
            ArtifactType.PROTOBUF, "graphql", ArtifactType.GRAPHQL, "xsd", ArtifactType.XSD, "wsdl",
            ArtifactType.WSDL);

    private static final Set<String> AVRO_TYPES = Set.of("record", "enum", "fixed");

    private final ArtifactTypeUtilProviderFactory factory;

    /**
     * Creates a detector backed by the given provider factory.
     *
     * @param factory the artifact type provider factory
     */
    public TypeDetector(ArtifactTypeUtilProviderFactory factory) {
        this.factory = factory;
    }

    /**
     * Detects the artifact type of the given source.
     *
     * @param source the source to inspect
     * @return the detected artifact type, or empty if it could not be determined
     */
    public Optional<String> detect(SchemaSource source) {
        if (source.artifactType().isPresent()) {
            return source.artifactType();
        }
        Optional<String> byExtension = byExtension(source.location());
        if (byExtension.isPresent()) {
            return byExtension;
        }
        Optional<String> structural = byStructure(source);
        if (structural.isPresent()) {
            return structural;
        }
        return byAccepters(source);
    }

    private Optional<String> byExtension(String location) {
        try {
            String name = location.substring(location.lastIndexOf('/') + 1);
            int dot = name.lastIndexOf('.');
            if (dot < 0) {
                return Optional.empty();
            }
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            Map<String, String> map = factory.getFileExtensionToTypeMap();
            // the factory normalizes keys to lower case with a leading dot
            String type = map.get("." + ext);
            if (type == null) {
                type = FALLBACK_EXTENSIONS.get(ext);
            }
            return Optional.ofNullable(type);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<String> byStructure(SchemaSource source) {
        try {
            JsonNode root = ContentTypeUtil.parseJsonOrYaml(source.content());
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            if (root.has("openapi") || root.has("swagger") || root.has("asyncapi") || root.has("openrpc")) {
                // apitomy-data-models only accepts Jackson 2 trees
                Document doc = Library
                        .readDocument((com.fasterxml.jackson.databind.node.ObjectNode) toJackson2(root));
                if (ModelTypeUtil.isAsyncApiModel(doc)) {
                    return Optional.of(ArtifactType.ASYNCAPI);
                }
                if (ModelTypeUtil.isOpenRpcModel(doc)) {
                    return Optional.of(ArtifactType.OPENRPC);
                }
                return Optional.of(ArtifactType.OPENAPI);
            }
            JsonNode schema = root.get("$schema");
            if (schema != null && !schema.isNull()) {
                return Optional.of(ArtifactType.JSON);
            }
            JsonNode name = root.get("name");
            JsonNode type = root.get("type");
            if (name != null && name.isString() && type != null && type.isString()
                    && AVRO_TYPES.contains(type.asString())) {
                return Optional.of(ArtifactType.AVRO);
            }
        } catch (Exception e) {
            // not JSON/YAML or not recognizable; fall through
        }
        return Optional.empty();
    }

    private Optional<String> byAccepters(SchemaSource source) {
        for (ArtifactTypeUtilProvider provider : factory.getAllArtifactTypeProviders()) {
            try {
                ContentAccepter accepter = provider.getContentAccepter();
                if (accepter != null && accepter.acceptsContent(source.content(), Map.of())) {
                    return Optional.of(provider.getArtifactType());
                }
            } catch (Exception e) {
                // try the next provider
            }
        }
        return Optional.empty();
    }

    /** apitomy-data-models only accepts Jackson 2 trees. */
    private static com.fasterxml.jackson.databind.JsonNode toJackson2(JsonNode node) throws IOException {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(node.toString());
    }
}
