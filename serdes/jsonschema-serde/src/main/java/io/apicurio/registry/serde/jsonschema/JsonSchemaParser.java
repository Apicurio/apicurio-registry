package io.apicurio.registry.serde.jsonschema;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

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
    public JsonSchema parseSchema(byte[] rawSchema, Map<String, ParsedSchema<JsonSchema>> resolvedReferences) {
        return new JsonSchema(IoUtil.toString(rawSchema), resolvedReferences.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getParsedSchema())), 0);
    }

    //TODO we could implement some way of providing the jsonschema beforehand:
    // - via annotation in the object being serialized
    // - via config property
    //if we do this users will be able to automatically registering the schema when using this serde

    /**
     * @see io.apicurio.registry.resolver.SchemaParser#getSchemaFromData(java.lang.Object)
     */
    @Override
    public ParsedSchema<JsonSchema> getSchemaFromData(Record<T> data) {
        //not supported for jsonschema type
        return null;
    }

    @Override
    public ParsedSchema<JsonSchema> getSchemaFromData(Record<T> data, boolean dereference) {
        //not supported for jsonschema type
        return null;
    }

    @Override
    public boolean supportsExtractSchemaFromData() {
        return false;
    }
}
