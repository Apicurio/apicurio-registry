package io.apicurio.registry.json.rules.compatibility.jsonschema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.DiffContext;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.Difference;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.SchemaDiffVisitor;
import io.apicurio.registry.json.rules.validity.JsonSchemaVersion;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SpecificationVersion;
import org.everit.json.schema.loader.internal.ReferenceResolver;
import org.json.JSONObject;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.json.rules.compatibility.jsonschema.JsonUtil.MAPPER;
import static io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;

public class JsonSchemaDiffLibrary {

    private static final String SCHEMA_KEYWORD = "$schema";
    private static final SchemaClient DENY_ALL_SCHEMA_CLIENT = url -> {
        throw new IllegalStateException("External JSON Schema resolution is disabled");
    };

    /**
     * Find and analyze differences between two JSON schemas.
     *
     * @param original Original/Previous/First/Left JSON schema representation
     * @param updated Updated/Next/Second/Right JSON schema representation
     * @param resolvedReferences
     * @return an object to access the found differences: Original -&gt; Updated
     * @throws IllegalArgumentException if the input is not a valid representation of a JsonSchema
     */
    public static DiffContext findDifferences(String original, String updated,
                                              Map<String, TypedContent> resolvedReferences) {
        try {
            JsonNode originalNode = MAPPER.readTree(original);
            JsonNode updatedNode = MAPPER.readTree(updated);

            JSONObject originalJson = MAPPER.readValue(original, JSONObject.class);
            JSONObject updatedJson = MAPPER.readValue(updated, JSONObject.class);

            SchemaLoader.SchemaLoaderBuilder originalSchemaBuilder = SchemaLoader.builder();
            loadReferences(originalNode, resolvedReferences, originalSchemaBuilder);

            Schema originalSchema = originalSchemaBuilder.schemaJson(originalJson).build().load().build();

            SchemaLoader.SchemaLoaderBuilder updatedSchemaBuilder = SchemaLoader.builder();
            loadReferences(updatedNode, resolvedReferences, updatedSchemaBuilder);

            Schema updatedSchema = updatedSchemaBuilder.schemaJson(updatedJson).build().load().build();

            return findDifferences(originalSchema, updatedSchema);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void loadReferences(JsonNode jsonNode, Map<String, TypedContent> resolvedReferences,
            SchemaLoader.SchemaLoaderBuilder schemaLoaderBuilder) {
        SpecificationVersion spec = SpecificationVersion.DRAFT_7;
        if (jsonNode.has(SCHEMA_KEYWORD)) {
            String schema = jsonNode.get(SCHEMA_KEYWORD).asText();
            if (schema != null) {
                spec = SpecificationVersion.lookupByMetaSchemaUrl(schema)
                        .orElse(SpecificationVersion.DRAFT_7);
            }
        }

        URI idUri = null;
        if (jsonNode.has(spec.idKeyword())) {
            String id = jsonNode.get(spec.idKeyword()).asText();
            if (id != null) {
                idUri = ReferenceResolver.resolve((URI) null, id);
            }
        }

        schemaLoaderBuilder.httpClient(DENY_ALL_SCHEMA_CLIENT);

        Set<URI> extractedReferences = JsonUtil.extractReferencesRecursive(JsonSchemaVersion.valueOf(spec.name()), idUri, jsonNode);
        for (URI extractedReference : extractedReferences) {
            var resolvedReferenceContent = resolvedReferences.get(extractedReference.toString());
            if (resolvedReferenceContent != null) {
                schemaLoaderBuilder.registerSchemaByURI(extractedReference,
                        new JSONObject(resolvedReferenceContent.getContent().content()));
            } else {
                /*
                 * Since we do not have the referenced content, we insert a placeholder schema, that will
                 * accept any JSON, to the reference lookup table of the library. This prevents the library
                 * from attempting to download the schema if `http://`, or trying to open a file if `file://`.
                 * This avoids potential security issues by us having to explicitly provide referenced
                 * content. For compatibility checks, we do not care about the reference format, while still
                 * requiring a valid URI.
                 */
                schemaLoaderBuilder.registerSchemaByURI(extractedReference, new JSONObject());
            }
        }
    }

    public static DiffContext findDifferences(Schema originalSchema, Schema updatedSchema) {
        DiffContext rootContext = DiffContext.createRootContext();
        new SchemaDiffVisitor(rootContext, originalSchema).visit(wrap(updatedSchema));
        return rootContext;
    }

    public static boolean isCompatible(String original, String updated,
            Map<String, TypedContent> resolvedReferences) {
        return findDifferences(original, updated, resolvedReferences).foundAllDifferencesAreCompatible();
    }

    public static Set<Difference> getIncompatibleDifferences(String original, String updated,
                                                             Map<String, TypedContent> resolvedReferences) {
        return findDifferences(original, updated, resolvedReferences).getIncompatibleDifferences();
    }
}
