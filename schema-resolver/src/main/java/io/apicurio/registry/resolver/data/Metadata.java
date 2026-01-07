package io.apicurio.registry.resolver.data;

import io.apicurio.registry.resolver.strategy.ArtifactReference;

public interface Metadata {

    public ArtifactReference artifactReference();

    /**
     * Returns the explicit schema content provided for this record, if any.
     * When a schema is explicitly provided (e.g., via message headers), it takes
     * precedence over schema inference from the data payload.
     *
     * @return the explicit schema content as a string, or null if no explicit schema is provided
     */
    default String explicitSchemaContent() {
        return null;
    }

    /**
     * Returns the type of the explicit schema provided for this record, if any.
     * The type should match one of the artifact types (e.g., "AVRO", "PROTOBUF", "JSON").
     *
     * @return the schema type, or null if no explicit schema type is provided
     */
    default String explicitSchemaType() {
        return null;
    }

}
