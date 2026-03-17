package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

/**
 * Data transfer object representing a reference from one artifact to another. Artifact references model
 * relationships such as {@code $ref} in JSON Schema, {@code import} in Protobuf, or type references in Avro.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class ArtifactReferenceDto {

    private String groupId;
    private String artifactId;
    private String version;
    private String name;
}
