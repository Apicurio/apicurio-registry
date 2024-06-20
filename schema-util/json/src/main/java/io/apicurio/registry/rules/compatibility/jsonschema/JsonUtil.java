package io.apicurio.registry.rules.compatibility.jsonschema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.RegistryException;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.SpecificationVersion;
import org.everit.json.schema.loader.internal.ReferenceResolver;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class JsonUtil {

    public static final ObjectMapper MAPPER;
    private static final String SCHEMA_KEYWORD = "$schema";

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JsonOrgModule());
        MAPPER.registerModule(new ParameterNamesModule());
        MAPPER.registerModule(new Jdk8Module());
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.registerModule(new JsonOrgModule());
        MAPPER.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        MAPPER.disable(FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    }

    public static Schema readSchema(String content) throws JsonProcessingException {
        return readSchema(content, Collections.emptyMap(), true);
    }

    public static Schema readSchema(String content, Map<String, TypedContent> resolvedReferences)
            throws JsonProcessingException {
        return readSchema(content, resolvedReferences, true);
    }

    public static Schema readSchema(String content, Map<String, TypedContent> resolvedReferences,
            boolean validateDangling) throws JsonProcessingException {
        JsonNode jsonNode = MAPPER.readTree(content);
        Schema schemaObj;
        // Extract the $schema to use for determining the id keyword
        SpecificationVersion spec = SpecificationVersion.DRAFT_7;
        if (jsonNode.has(SCHEMA_KEYWORD)) {
            String schema = jsonNode.get(SCHEMA_KEYWORD).asText();
            if (schema != null) {
                spec = SpecificationVersion.lookupByMetaSchemaUrl(schema)
                        .orElse(SpecificationVersion.DRAFT_7);
            }
        }
        // Extract the $id to use for resolving relative $ref URIs
        URI idUri = null;
        if (jsonNode.has(spec.idKeyword())) {
            String id = jsonNode.get(spec.idKeyword()).asText();
            if (id != null) {
                idUri = ReferenceResolver.resolve((URI) null, id);
            }
        }
        // First extract all references
        var refNodes = jsonNode.findValues("$ref");
        var refStrings = refNodes.stream().filter(JsonNode::isTextual).map(TextNode.class::cast)
                .map(TextNode::textValue).collect(Collectors.toList());

        SchemaLoader.SchemaLoaderBuilder builder = SchemaLoader.builder().useDefaults(true).draftV7Support();

        var resolvedReferencesCopy = new HashMap<>(resolvedReferences);
        var referenceURIs = new ArrayList<>();
        for (String refString : refStrings) {
            URI referenceURI = ReferenceResolver.resolve(idUri, refString);
            referenceURIs.add(referenceURI);
            var resolvedReference = resolvedReferencesCopy.remove(referenceURI.toString());
            if (resolvedReference != null) {
                builder.registerSchemaByURI(referenceURI,
                        new JSONObject(resolvedReference.getContent().content()));
            } else {
                /*
                 * Since we do not have the referenced content, we insert a placeholder schema, that will
                 * accept any JSON, to the reference lookup table of the library. This prevents the library
                 * from attempting to download the schema if `http://`, or trying to open a file if `file://`.
                 * This avoids potential security issues by us having to explicitly provide referenced
                 * content. For validation, we do not care about the reference format, while still requiring a
                 * valid URI.
                 */
                builder.registerSchemaByURI(referenceURI, new JSONObject());
            }
        }
        // Check for dangling references. Do we want to do this as a separate rule?
        if (validateDangling && !resolvedReferencesCopy.isEmpty()) {
            var msg = "There are unused references recorded for this content. "
                    + "Make sure you have not made a typo, otherwise remove the unused reference record(s). "
                    + "References in the content: " + referenceURIs + ", " + "Unused reference records: "
                    + new ArrayList<>(resolvedReferencesCopy.keySet());
            throw new RegistryException(msg);
        }

        JSONObject jsonObject = MAPPER.treeToValue((jsonNode), JSONObject.class);
        builder.schemaJson(jsonObject);
        SchemaLoader loader = builder.build();
        schemaObj = loader.load().build();
        return schemaObj;
    }
}
