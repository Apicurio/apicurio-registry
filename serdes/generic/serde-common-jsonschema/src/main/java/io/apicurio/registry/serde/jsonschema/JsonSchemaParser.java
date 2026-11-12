package io.apicurio.registry.serde.jsonschema;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonSchemaParser<T> implements SchemaParser<Schema, T> {
    /**
     * @see io.apicurio.registry.resolver.SchemaParser#artifactType()
     */
    @Override
    public String artifactType() {
        return ArtifactType.JSON;
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#parseSchema(byte[], Map)
     */
    @Override
    public Schema parseSchema(byte[] rawSchema,
            Map<String, ParsedSchema<Schema>> resolvedReferences) {
        Map<String, String> referenceSchemas = new HashMap<>();

        resolveReferences(resolvedReferences, referenceSchemas);

        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7,
                builder -> builder.schemas(referenceSchemas));

        return registry.getSchema(IoUtil.toString(rawSchema));
    }

    private void resolveReferences(Map<String, ParsedSchema<Schema>> resolvedReferences,
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
     * @see io.apicurio.registry.resolver.SchemaParser#getSchemaFromData(Record)
     */
    @Override
    public ParsedSchema<Schema> getSchemaFromData(Record<T> data) {
        // not supported for jsonschema type
        return null;
    }

    @Override
    public ParsedSchema<Schema> getSchemaFromData(Record<T> data, boolean dereference) {
        // not supported for jsonschema type
        return null;
    }

    @Override
    public ParsedSchema<Schema> getSchemaFromLocation(String location) {
        String rawSchema = IoUtil
                .toString(Thread.currentThread().getContextClassLoader().getResourceAsStream(location));
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);
        return new ParsedSchemaImpl<Schema>()
                .setParsedSchema(registry.getSchema(IoUtil.toStream(rawSchema)))
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
