package io.apicurio.registry.json.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SpecVersion.VersionFlag;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinderException;
import io.apicurio.registry.json.rules.validity.JsonSchemaDocumentValidator;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A JSON Schema implementation of a reference finder.
 */
public class JsonSchemaReferenceFinder implements ReferenceFinder {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final JsonSchemaDocumentValidator DRAFT_DETECTOR =
            JsonSchemaDocumentValidator.builder()
                    .draftDetector(JsonSchemaDocumentValidator.dataModelsDraftDetector())
                    .treatUnrecognisedDraftAs(VersionFlag.V7)
                    .build();

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            JsonNode tree = mapper.readTree(content.getContent().content());
            String idKeyword = DRAFT_DETECTOR.draftOf(tree).orElse(VersionFlag.V7) == VersionFlag.V4
                    ? "id" : "$id";

            Set<String> externalTypes = new HashSet<>();
            findExternalTypesIn(tree, null, idKeyword, externalTypes);

            return externalTypes.stream().map(JsonPointerExternalReference::new)
                    .filter(ref -> ref.getResource() != null)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new ReferenceFinderException("Error finding external references in a JSON Schema file.", e);
        }
    }

    private static void findExternalTypesIn(JsonNode schema, URI baseUri, String idKeyword,
            Set<String> externalTypes) {
        if (schema.isObject()) {
            JsonNode idNode = schema.get(idKeyword);
            if (idNode != null && idNode.isTextual()) {
                baseUri = resolve(baseUri, idNode.asText());
            }

            if (schema.has("$ref")) {
                String ref = schema.get("$ref").asText(null);
                if (ref != null) {
                    externalTypes.add(resolve(baseUri, ref).toString());
                }
            }

            Iterator<Entry<String, JsonNode>> fields = schema.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> field = fields.next();
                findExternalTypesIn(field.getValue(), baseUri, idKeyword, externalTypes);
            }
        } else if (schema.isArray()) {
            for (JsonNode innerNode : schema) {
                findExternalTypesIn(innerNode, baseUri, idKeyword, externalTypes);
            }
        }
    }

    private static URI resolve(URI baseUri, String reference) {
        URI uri = URI.create(reference);
        return baseUri == null ? uri : baseUri.resolve(uri);
    }
}
