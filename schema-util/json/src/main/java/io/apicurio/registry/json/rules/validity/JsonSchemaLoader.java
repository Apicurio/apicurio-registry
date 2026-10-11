package io.apicurio.registry.json.rules.validity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.SpecVersion.VersionFlag;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.types.RegistryException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS;

/**
 * Checks that the content of a JSON Schema artifact is a valid JSON Schema, in any of drafts 4, 6,
 * 7, 2019-09 and 2020-12. The draft is chosen as the compatibility checker chooses it, and a
 * {@code $schema} that names no known draft is read as draft 7. A reference recorded for the
 * artifact that the content doesn't use is rejected.
 */
final class JsonSchemaLoader {

    private static final JsonSchemaDocumentValidator VALIDATOR = JsonSchemaDocumentValidator.builder()
            .draftDetector(JsonSchemaDocumentValidator.dataModelsDraftDetector())
            .treatUnrecognisedDraftAs(VersionFlag.V7)
            .loadSchema(true)
            .build();

    // Numbers reach the validator as written, rather than rounded through a double.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private JsonSchemaLoader() {
    }

    /**
     * @return each problem found; empty if the content is a valid JSON Schema
     */
    static List<RuleViolation> load(String content, Map<String, TypedContent> resolvedReferences)
            throws JsonProcessingException {
        var jsonNode = MAPPER.readTree(content);
        var draft = VALIDATOR.draftOf(jsonNode).orElseThrow();

        var references = findReferences(draft == VersionFlag.V4 ? "id" : "$id", null, jsonNode);
        checkNoUnusedReferences(references, resolvedReferences);

        return VALIDATOR.validate(jsonNode, referencedDocuments(draft, references, resolvedReferences)::get);
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
     * The content of each document the schema refers to, by the reference without its fragment.
     * <p>
     * Validity doesn't depend on what a reference points to, so a document Registry has no content
     * for is replaced by one that accepts anything. It contains each JSON Pointer and each anchor the
     * schema refers to it with, so references into it resolve too.
     */
    private static Map<String, String> referencedDocuments(VersionFlag draft, Set<URI> references,
            Map<String, TypedContent> resolvedReferences) {
        var supplied = new HashMap<String, String>();
        var placeholders = new HashMap<String, ObjectNode>();
        for (var reference : references) {
            var document = withoutFragment(reference);
            var content = resolvedReferences.get(reference.toString());
            if (content != null) {
                supplied.put(document, content.getContent().content());
            } else {
                var placeholder = placeholders.computeIfAbsent(document, d -> MAPPER.createObjectNode());
                var fragment = reference.getFragment();
                if (fragment != null && fragment.startsWith("/")) {
                    addPointer(placeholder, fragment);
                } else if (fragment != null && !fragment.isEmpty()) {
                    addAnchor(placeholder, draft, fragment);
                }
            }
        }
        var documents = new HashMap<String, String>();
        placeholders.forEach((document, placeholder) -> documents.put(document, placeholder.toString()));
        documents.putAll(supplied);
        return documents;
    }

    private static String withoutFragment(URI reference) {
        var text = reference.toString();
        var hash = text.indexOf('#');
        return hash < 0 ? text : text.substring(0, hash);
    }

    /**
     * Adds a schema that accepts anything and is named by the given anchor, as the referencing draft
     * spells one: {@code $anchor} from 2019-09, a location-independent identifier before that. The
     * referenced document has no {@code $schema}, so it is read in the referencing schema's draft.
     */
    private static void addAnchor(ObjectNode placeholder, VersionFlag draft, String anchor) {
        var modern = draft == VersionFlag.V201909 || draft == VersionFlag.V202012;
        var definitions = placeholder.get(modern ? "$defs" : "definitions") instanceof ObjectNode existing
                ? existing : placeholder.putObject(modern ? "$defs" : "definitions");
        var definition = definitions.putObject("anchor-" + anchor);
        if (modern) {
            definition.put("$anchor", anchor);
        } else {
            definition.put(draft == VersionFlag.V4 ? "id" : "$id", "#" + anchor);
        }
    }

    /** Adds an empty schema, which accepts anything, at the given JSON Pointer. */
    private static void addPointer(ObjectNode placeholder, String pointer) {
        var node = placeholder;
        for (var token : pointer.substring(1).split("/", -1)) {
            var name = token.replace("~1", "/").replace("~0", "~");
            node = node.get(name) instanceof ObjectNode child ? child : node.putObject(name);
        }
    }

    /**
     * Every {@code $ref} in the schema, resolved against the {@code $id} in scope where it appears.
     * There can be multiple {@code $id} keywords nested within the root schema resource, see
     * <a href="https://json-schema.org/blog/posts/understanding-lexical-dynamic-scopes">Understanding JSON Schema Lexical and Dynamic Scopes</a>.
     */
    private static Set<URI> findReferences(String idKeyword, URI idURI, JsonNode jsonNode) {
        var result = new HashSet<URI>();
        if (jsonNode instanceof ObjectNode objectNode) {
            var idNode = objectNode.get(idKeyword);
            if (idNode != null && idNode.isTextual()) {
                idURI = resolve(null, idNode.textValue());
            }
            for (var property : objectNode.properties()) {
                if ("$ref".equals(property.getKey())) {
                    if (property.getValue().isTextual()) {
                        result.add(resolve(idURI, property.getValue().textValue()));
                    }
                } else {
                    result.addAll(findReferences(idKeyword, idURI, property.getValue()));
                }
            }
        } else if (jsonNode.isArray()) {
            for (var element : jsonNode) {
                result.addAll(findReferences(idKeyword, idURI, element));
            }
        }
        return result;
    }

    private static URI resolve(URI base, String reference) {
        try {
            return base == null ? new URI(reference) : base.resolve(reference);
        } catch (URISyntaxException e) {
            throw new RegistryException("Invalid reference: " + reference, e);
        }
    }
}
