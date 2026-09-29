package io.apicurio.registry.json.rules.validity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.SchemaLoaderConfig;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.types.RegistryException;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.internal.ReferenceResolver;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS;
import static io.apicurio.registry.json.rules.validity.JsonSchemaVersion.DRAFT_7;
import static io.apicurio.registry.json.rules.validity.JsonSchemaVersion.UNKNOWN;
import static io.apicurio.registry.json.rules.validity.JsonSchemaVersion.detect;

/**
 * Checks that a JSON Schema is valid by loading it into a schema library: everit for drafts 4, 6
 * and 7, jsonsKema for 2020-12. Loading fails if the schema is invalid, if it is draft 2019-09,
 * which neither library here supports, or if a reference recorded for the artifact is not used by
 * the content.
 */
final class JsonSchemaLoader {

    private static final SchemaClient DENY_ALL_SCHEMA_CLIENT = url -> {
        throw new IllegalStateException("External JSON Schema resolution is disabled");
    };

    // Numbers reach the schema library as written, rather than rounded through a double.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JsonOrgModule())
            .enable(USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private JsonSchemaLoader() {
    }

    static void load(String content, Map<String, TypedContent> resolvedReferences)
            throws JsonProcessingException {
        var jsonNode = MAPPER.readTree(content);

        var version = detect(jsonNode);
        if (version == UNKNOWN) {
            // TODO: Make this configurable? Throw an exception?
            version = DRAFT_7;
        }

        var references = findReferences(version, null, jsonNode);
        checkNoUnusedReferences(references, resolvedReferences);

        // A reference Registry has no content for is loaded as a schema that accepts anything. This keeps
        // the library from downloading it if it is http://, or opening a file if it is file://, so the only
        // content ever read is what Registry supplies. Validity does not depend on what a reference points
        // to, only on it being a valid URI.
        var referencedContent = new HashMap<URI, String>();
        for (var reference : references) {
            var supplied = resolvedReferences.get(reference.toString());
            referencedContent.put(reference, supplied != null ? supplied.getContent().content() : "{}");
        }

        switch (version) {
            case DRAFT_4, DRAFT_6, DRAFT_7 -> loadWithEverit(jsonNode, referencedContent);
            case DRAFT_2019_09 -> throw new RegistryException("JSON schema version 2019-09 is not supported yet.");
            case DRAFT_2020_12 -> loadWithJsonsKema(jsonNode, referencedContent);
            default -> throw new UnreachableCodeException("Unhandled case " + version);
        }
    }

    // Do we want to do this as a separate rule?
    private static void checkNoUnusedReferences(Set<URI> references, Map<String, TypedContent> resolvedReferences) {
        var unused = new HashSet<>(resolvedReferences.keySet());
        references.stream().map(URI::toString).forEach(unused::remove);
        if (!unused.isEmpty()) {
            throw new RegistryException("""
                    There are unused references recorded for this content. \
                    Make sure you have not made a typo, otherwise remove the unused reference record(s). \
                    References in the content: %s. \
                    Unused reference records: %s."""
                    .formatted(
                            references.stream().map(URI::toString).collect(Collectors.joining(", ")),
                            String.join(", ", unused)
                    ));
        }
    }

    /**
     * Every {@code $ref} in the schema, resolved against the {@code $id} in scope where it appears.
     * There can be multiple {@code $id} keywords nested within the root schema resource, see
     * <a href="https://json-schema.org/blog/posts/understanding-lexical-dynamic-scopes">Understanding JSON Schema Lexical and Dynamic Scopes</a>.
     */
    private static Set<URI> findReferences(JsonSchemaVersion version, URI idURI, JsonNode jsonNode) {
        var result = new HashSet<URI>();
        if (jsonNode instanceof ObjectNode objectNode) {
            var idNode = objectNode.get(version.getIdKeyword());
            if (idNode != null && idNode.isTextual()) {
                idURI = ReferenceResolver.resolve((URI) null, idNode.textValue());
            }
            for (var property : objectNode.properties()) {
                if ("$ref".equals(property.getKey())) {
                    if (property.getValue().isTextual()) {
                        result.add(ReferenceResolver.resolve(idURI, property.getValue().textValue()));
                    }
                } else {
                    result.addAll(findReferences(version, idURI, property.getValue()));
                }
            }
        } else if (jsonNode.isArray()) {
            for (var element : jsonNode) {
                result.addAll(findReferences(version, idURI, element));
            }
        }
        return result;
    }

    private static void loadWithEverit(JsonNode jsonNode, Map<URI, String> referencedContent)
            throws JsonProcessingException {
        var builder = SchemaLoader.builder().useDefaults(true).draftV7Support().httpClient(DENY_ALL_SCHEMA_CLIENT);
        referencedContent.forEach((reference, content) -> builder.registerSchemaByURI(reference, new JSONObject(content)));
        builder.schemaJson(MAPPER.treeToValue(jsonNode, JSONObject.class));
        builder.build().load().build();
    }

    private static void loadWithJsonsKema(JsonNode jsonNode, Map<URI, String> referencedContent)
            throws JsonProcessingException {
        var config = SchemaLoaderConfig.createDefaultConfig(referencedContent);
        var json = new JsonParser(MAPPER.writeValueAsString(jsonNode)).parse();
        new com.github.erosb.jsonsKema.SchemaLoader(json, config).load();
    }
}
