package io.apicurio.registry.rules.compatibility.jsonschema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.SchemaLoaderConfig;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.rules.SomeJsonSchema;
import io.apicurio.registry.rules.validity.JsonSchemaVersion;
import io.apicurio.registry.types.RegistryException;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.internal.ReferenceResolver;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS;
import static io.apicurio.registry.rules.validity.JsonSchemaVersion.DRAFT_7;
import static io.apicurio.registry.rules.validity.JsonSchemaVersion.UNKNOWN;
import static io.apicurio.registry.rules.validity.JsonSchemaVersion.detect;

public class JsonUtil {

    public static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JsonOrgModule());
        MAPPER.registerModule(new ParameterNamesModule());
        MAPPER.registerModule(new Jdk8Module());
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.registerModule(new JsonOrgModule());
        MAPPER.enable(USE_BIG_DECIMAL_FOR_FLOATS);
        MAPPER.disable(FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    }

    public static void readSchema(String content, Map<String, TypedContent> resolvedReferences)
            throws JsonProcessingException {
        readSchema(content, resolvedReferences, true);
    }

    public static SomeJsonSchema readSchema(String content, Map<String, TypedContent> resolvedReferences,
                                            boolean validateDangling) throws JsonProcessingException {

        var jsonNode = MAPPER.readTree(content);

        var specVersion = detect(jsonNode);

        if (specVersion == UNKNOWN) {
            // TODO: Make this configurable? Throw an exception?
            specVersion = DRAFT_7;
        }

        var referenceURIs = extractReferencesRecursive(specVersion, null, jsonNode);

        var resolvedReferencesCopy = new HashMap<>(resolvedReferences);
        referenceURIs.stream().map(URI::toString).forEach(resolvedReferencesCopy::remove);
        // Check for dangling references. Do we want to do this as a separate rule?
        if (validateDangling && !resolvedReferencesCopy.isEmpty()) {
            var msg = """
                    There are unused references recorded for this content. \
                    Make sure you have not made a typo, otherwise remove the unused reference record(s). \
                    References in the content: %s. \
                    Unused reference records: %s."""
                    .formatted(
                            referenceURIs.stream().map(URI::toString).collect(Collectors.joining(", ")),
                            resolvedReferencesCopy.keySet().stream().collect(Collectors.joining(", "))
                    );
            throw new RegistryException(msg);
        }

        switch (specVersion) { // TODO
            case DRAFT_4:
            case DRAFT_6:
            case DRAFT_7:
                return readSchemaEverit(jsonNode, resolvedReferences, referenceURIs);
            case DRAFT_2019_09:
                throw new RegistryException("JSON schema version 2019-09 is not supported yet.");
            case DRAFT_2020_12:
                return readSchemaSKema(jsonNode, resolvedReferences, referenceURIs);
            default:
                throw new UnreachableCodeException("Unhandled case " + specVersion);
        }
    }

    /**
     * NOTE: There can be multiple $id keywords nested within the root schema resource, see
     * <a href="https://json-schema.org/blog/posts/understanding-lexical-dynamic-scopes">Understanding JSON Schema Lexical and Dynamic Scopes</a>.
     */
    private static Set<URI> extractReferencesRecursive(JsonSchemaVersion specVersion, URI idURI, JsonNode jsonNode) {
        var result = new HashSet<URI>();
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            var idNode = objectNode.get(specVersion.getIdKeyword());
            if (idNode != null && idNode.isTextual()) {
                idURI = ReferenceResolver.resolve((URI) null, idNode.textValue());
            }
            for (Iterator<Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext(); ) {
                Entry<String, JsonNode> nested = it.next();
                if ("$ref".equals(nested.getKey())) {
                    var refNode = nested.getValue();
                    if (refNode.isTextual()) {
                        URI referenceURI = ReferenceResolver.resolve(idURI, refNode.textValue());
                        result.add(referenceURI);
                    }
                } else {
                    var referenceURIs = extractReferencesRecursive(specVersion, idURI, nested.getValue());
                    result.addAll(referenceURIs);
                }
            }
        } else if (jsonNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) jsonNode;
            for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext(); ) {
                JsonNode nested = it.next();
                var referenceURIs = extractReferencesRecursive(specVersion, idURI, nested);
                result.addAll(referenceURIs);
            }
        }
        return result;
    }

    private static SomeJsonSchema readSchemaEverit(JsonNode jsonNode, Map<String, TypedContent> resolvedReferences, Set<URI> extractedReferences) throws JsonProcessingException {
        var builder = SchemaLoader.builder().useDefaults(true).draftV7Support();
        for (URI extractedReference : extractedReferences) {
            var resolvedReferenceContent = resolvedReferences.get(extractedReference.toString());
            if (resolvedReferenceContent != null) {
                builder.registerSchemaByURI(extractedReference, new JSONObject(resolvedReferenceContent.getContent().content()));
            } else {
                /*
                 * Since we do not have the referenced content, we insert a placeholder schema, that will
                 * accept any JSON, to the reference lookup table of the library. This prevents the library
                 * from attempting to download the schema if `http://`, or trying to open a file if `file://`.
                 * This avoids potential security issues by us having to explicitly provide referenced
                 * content. For validation, we do not care about the reference format, while still requiring a
                 * valid URI.
                 */
                builder.registerSchemaByURI(extractedReference, new JSONObject());
            }
        }
        var jsonObject = MAPPER.treeToValue((jsonNode), JSONObject.class);
        builder.schemaJson(jsonObject);
        var loader = builder.build();
        var schema = loader.load().build();
        return new SomeJsonSchema(schema);
    }

    private static SomeJsonSchema readSchemaSKema(JsonNode jsonNode, Map<String, TypedContent> resolvedReferences, Set<URI> extractedReferences) throws JsonProcessingException {
        var resolved = new HashMap<URI, String>();
        for (URI extractedReference : extractedReferences) {
            var resolvedReferenceContent = resolvedReferences.get(extractedReference.toString());
            if (resolvedReferenceContent != null) {
                resolved.put(extractedReference, resolvedReferenceContent.getContent().content());
            } else {
                /*
                 * Since we do not have the referenced content, we insert a placeholder schema, that will
                 * accept any JSON, to the reference lookup table of the library. This prevents the library
                 * from attempting to download the schema if `http://`, or trying to open a file if `file://`.
                 * This avoids potential security issues by us having to explicitly provide referenced
                 * content. For validation, we do not care about the reference format, while still requiring a
                 * valid URI.
                 */
                resolved.put(extractedReference, "{}");
            }
        }
        SchemaLoaderConfig config = SchemaLoaderConfig.createDefaultConfig(resolved);
        var jsonValue = new JsonParser(MAPPER.writeValueAsString(jsonNode)).parse();
        var schema = new com.github.erosb.jsonsKema.SchemaLoader(jsonValue, config).load();
        return new SomeJsonSchema(schema, jsonNode);
    }
}
