package io.apicurio.registry.content.refs.resolve;

/**
 * A schema source together with the metadata extracted while indexing it.
 *
 * @param source the schema source
 * @param artifactType the detected artifact type
 * @param avroFullName the Avro full name, or null when not an Avro named type
 * @param jsonSchemaId the JSON Schema {@code $id} (fragment stripped), or null when absent
 */
public record IndexedSource(SchemaSource source, String artifactType, String avroFullName,
        String jsonSchemaId) {
}
