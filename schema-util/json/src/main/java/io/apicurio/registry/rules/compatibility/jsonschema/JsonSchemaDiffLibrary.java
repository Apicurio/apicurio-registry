package io.apicurio.registry.rules.compatibility.jsonschema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffContext;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.SchemaDiffVisitor;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.SpecificationVersion;
import org.everit.json.schema.loader.internal.ReferenceResolver;
import org.json.JSONObject;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.rules.compatibility.jsonschema.JsonUtil.MAPPER;
import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;

public class JsonSchemaDiffLibrary {

    private static final String SCHEMA_KEYWORD = "$schema";

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
            Map<String, ContentHandle> resolvedReferences) {
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

    private static void loadReferences(JsonNode jsonNode, Map<String, ContentHandle> resolvedReferences,
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

        for (Map.Entry<String, ContentHandle> stringStringEntry : resolvedReferences.entrySet()) {
            URI child = ReferenceResolver.resolve(idUri, stringStringEntry.getKey());
            schemaLoaderBuilder.registerSchemaByURI(child,
                    new JSONObject(stringStringEntry.getValue().content()));
        }
    }

    public static DiffContext findDifferences(Schema originalSchema, Schema updatedSchema) {
        DiffContext rootContext = DiffContext.createRootContext();
        new SchemaDiffVisitor(rootContext, originalSchema).visit(wrap(updatedSchema));
        return rootContext;
    }

    public static boolean isCompatible(String original, String updated,
            Map<String, ContentHandle> resolvedReferences) {
        return findDifferences(original, updated, resolvedReferences).foundAllDifferencesAreCompatible();
    }

    public static Set<Difference> getIncompatibleDifferences(String original, String updated,
            Map<String, ContentHandle> resolvedReferences) {
        return findDifferences(original, updated, resolvedReferences).getIncompatibleDifferences();
    }
}