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
            boolean registered = false;
            for (Map.Entry<String, TypedContent> entry : resolvedReferences.entrySet()) {
                URI resolvedReferenceUri = ReferenceResolver.resolve(idUri, entry.getKey());
                if (extractedReference.equals(resolvedReferenceUri)) {
                    schemaLoaderBuilder.registerSchemaByURI(extractedReference,
                            new JSONObject(entry.getValue().getContent().content()));
                    registered = true;
                    break;
                }
            }

            if (!registered) {
                /*
                 * We do not have the referenced content, so compatibility checks must fail closed rather
                 * than silently treating the reference as an accept-all schema. That keeps the result
                 * explicit and prevents unresolved references from being interpreted as compatible.
                 */
                throw new IllegalStateException("Unresolved JSON Schema reference: " + extractedReference);
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
