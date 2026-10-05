package io.apicurio.registry.json.rules.compatibility.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.apicurio.registry.json.rules.validity.JsonSchemaVersion;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.internal.ReferenceResolver;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS;

/**
 * Helpers for the legacy compatibility checker in this package. The validity rule loads schemas with
 * its own {@code JsonSchemaLoader}, so nothing outside the legacy checker depends on this class.
 */
public class JsonUtil {

    static final SchemaClient DENY_ALL_SCHEMA_CLIENT = url -> {
        throw new IllegalStateException("External JSON Schema resolution is disabled");
    };

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

    /**
     * NOTE: There can be multiple $id keywords nested within the root schema resource, see
     * <a href="https://json-schema.org/blog/posts/understanding-lexical-dynamic-scopes">Understanding JSON Schema Lexical and Dynamic Scopes</a>.
     */
    static Set<URI> extractReferencesRecursive(JsonSchemaVersion specVersion, URI idURI, JsonNode jsonNode) {
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

    /**
     * Determines whether an extracted reference URI is an internal fragment reference — i.e. it
     * resolves within the same schema document rather than pointing to an external resource. Internal
     * fragment references (e.g. {@code #/definitions/Foo}) are resolved by the schema loader from
     * the document itself and must not be treated as unresolved external references.
     */
    static boolean isInternalFragment(URI extractedReference, URI idUri) {
        String extractedStr = extractedReference.toString();
        String baseOfExtracted = extractedStr.contains("#")
                ? extractedStr.substring(0, extractedStr.indexOf('#'))
                : extractedStr;

        if (idUri == null) {
            return baseOfExtracted.isEmpty();
        }

        String idStr = idUri.toString();
        String baseOfId = idStr.contains("#")
                ? idStr.substring(0, idStr.indexOf('#'))
                : idStr;

        return baseOfExtracted.equals(baseOfId);
    }
}
