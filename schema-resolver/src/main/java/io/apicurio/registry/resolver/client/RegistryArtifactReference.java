package io.apicurio.registry.resolver.client;

import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor(access = PRIVATE)
@Builder
@Getter
@EqualsAndHashCode
@ToString
public class RegistryArtifactReference {

    private String name;
    private String groupId;
    private String artifactId;
    private String version;

    public static RegistryArtifactReference fromClientArtifactReference(ArtifactReference ref) {
        return RegistryArtifactReference.builder()
                .name(ref.getName())
                .groupId(ref.getGroupId())
                .artifactId(ref.getArtifactId())
                .version(ref.getVersion())
                .build();
    }

    public static RegistryArtifactReference fromClientArtifactReference(io.apicurio.registry.rest.client.v2.models.ArtifactReference ref) {
        return RegistryArtifactReference.builder()
                .name(ref.getName())
                .groupId(ref.getGroupId())
                .artifactId(ref.getArtifactId())
                .version(ref.getVersion())
                .build();
    }

    // TODO: How can we be sure that SchemaLookupResult contains the GAV?
    public static RegistryArtifactReference fromSchemaLookupResult(SchemaLookupResult<?> refLookup) {
        return RegistryArtifactReference.builder()
                .name(refLookup.getParsedSchema().referenceName())
                .groupId(refLookup.getGroupId())
                .artifactId(refLookup.getArtifactId())
                .version(refLookup.getVersion())
                .build();
    }
}
