package io.apicurio.registry.resolver.strategy;

import io.apicurio.registry.resolver.ParsedSchema;

/**
 * Input to a {@link ReferenceNamingStrategy}.
 *
 * @param referenceName the reference name as written in the parent (Avro full name, Protobuf import path)
 * @param artifactType the artifact type, for example {@code AVRO} or {@code PROTOBUF}
 * @param defaultGroupId the group the resolver would use without a naming strategy; may be null
 * @param namespace the Avro namespace or Protobuf package of the referenced schema; may be null
 * @param schema the referenced schema
 */
public record ReferenceNamingContext(String referenceName, String artifactType, String defaultGroupId,
        String namespace, ParsedSchema<?> schema) {
}
