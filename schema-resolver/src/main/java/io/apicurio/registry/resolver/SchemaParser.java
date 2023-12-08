package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.data.Record;

import java.util.Map;

public interface SchemaParser<S, U> {

    String artifactType();

    S parseSchema(byte[] rawSchema, Map<String, ParsedSchema<S>> resolvedReferences);

    /**
     * In some artifact types, such as AVRO, it is possible to extract the schema from the java object.
     * But this can be easily extended to other formats by using a custom {@link Record} implementation that adds additional fields
     * that allows to build a {@link ParsedSchema}
     *
     * @param data
     * @return the ParsedSchema, containing both the raw schema (bytes) and the parsed schema. Can be null.
     */
    ParsedSchema<S> getSchemaFromData(Record<U> data);

    /**
     * In some artifact types, such as AVRO, it is possible to extract the schema from the java object.
     * But this can be easily extended to other formats by using a custom {@link Record} implementation that adds additional fields
     * that allows to build a {@link ParsedSchema}
     *
     * @param data
     * @param dereference indicate the schema parser whether to try to dereference the record schema.
     * @return the ParsedSchema, containing both the raw schema (bytes) and the parsed schema. Can be null.
     */
    ParsedSchema<S> getSchemaFromData(Record<U> data, boolean dereference);

    /**
     * Flag that indicates if {@link SchemaParser#getSchemaFromData(Record)} is implemented or not.
     */
    default boolean supportsExtractSchemaFromData() {
        return true;
    }
}
