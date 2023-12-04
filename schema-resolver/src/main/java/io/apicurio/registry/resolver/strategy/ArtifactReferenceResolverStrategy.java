package io.apicurio.registry.resolver.strategy;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;

/**
 * This interface is used by the SchemaResolver to determine
 * the {@link ArtifactReference} under which the message schemas are located or should be registered
 * in the registry.
 *
 */
public interface ArtifactReferenceResolverStrategy<SCHEMA, DATA> {

    /**
     * For a given Record, returns the {@link ArtifactReference} under which the message schemas are located or should be registered
     * in the registry.
     * @param data record for which we want to resolve the ArtifactReference
     * @param parsedSchema the schema of the record being resolved, can be null if {@link ArtifactReferenceResolverStrategy#loadSchema()} is set to false
     * @return the {@link ArtifactReference} under which the message schemas are located or should be registered
     */
    ArtifactReference artifactReference(Record<DATA> data, ParsedSchema<SCHEMA> parsedSchema);

    /**
     * Whether or not to load and pass the parsed schema to the {@link ArtifactReferenceResolverStrategy#artifactReference(Record, ParsedSchema)} lookup method
     */
    default boolean loadSchema() {
        return true;
    }

}
