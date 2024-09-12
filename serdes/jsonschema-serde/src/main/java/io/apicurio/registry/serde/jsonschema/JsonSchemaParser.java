package io.apicurio.registry.serde.jsonschema;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonSchemaParser<T> implements SchemaParser<JsonSchema, T> {
    /**
     * @see io.apicurio.registry.serde.SchemaParser#artifactType()
     */
    @Override
    public String artifactType() {
        return ArtifactType.JSON;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
     */
    @Override
    public JsonSchema parseSchema(byte[] rawSchema,
            Map<String, ParsedSchema<JsonSchema>> resolvedReferences) {
        Map<String, String> referenceSchemas = new HashMap<>();

        resolveReferences(resolvedReferences, referenceSchemas);

        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7,
                builder -> builder.schemaLoaders(schemaLoaders -> schemaLoaders.schemas(referenceSchemas)));

        return schemaFactory.getSchema(IoUtil.toString(rawSchema));
    }

    private void resolveReferences(Map<String, ParsedSchema<JsonSchema>> resolvedReferences,
            Map<String, String> referenceSchemas) {
        resolvedReferences.forEach((referenceName, schema) -> {
            if (schema.hasReferences()) {
                resolveReferences(schema.getSchemaReferences().stream()
                        .collect(Collectors.toMap(parsedSchema -> parsedSchema.getParsedSchema().getId(),
                                parsedSchema -> parsedSchema)),
                        referenceSchemas);

            }
            referenceSchemas.put(schema.getParsedSchema().getId(), IoUtil.toString(schema.getRawSchema()));
        });
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#getSchemaFromData(java.lang.Object)
     */
    @Override
    public ParsedSchema<JsonSchema> getSchemaFromData(Record<T> data) {
        // not supported for jsonschema type
        return null;
    }

    @Override
    public ParsedSchema<JsonSchema> getSchemaFromData(Record<T> data, boolean dereference) {
        // not supported for jsonschema type
        return null;
    }

    @Override
    public ParsedSchema<JsonSchema> getSchemaFromLocation(String location) {
        String rawSchema = IoUtil
                .toString(Thread.currentThread().getContextClassLoader().getResourceAsStream(location));
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        return new ParsedSchemaImpl<JsonSchema>()
                .setParsedSchema(factory.getSchema(IoUtil.toStream(rawSchema)))
                .setRawSchema(rawSchema.getBytes());
    }

    @Override
    public boolean supportsExtractSchemaFromData() {
        return false;
    }

    @Override
    public boolean supportsGetSchemaFromLocation() {
        return true;
    }
}
