package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor(access = PRIVATE)
@Builder
@Getter
@ToString
public class SchemaLookupResult<T> {

    private ParsedSchema<T> parsedSchema;

    private long globalId;
    private long contentId;
    private String contentHash;
    private String groupId;
    private String artifactId;
    private String version;

    /**
     * The artifact references used when registering this schema.
     * Used for content-based cache key generation.
     */
    @Builder.Default
    private Set<ArtifactReference> references = new HashSet<>();


    public ArtifactReference toArtifactReference() {
        return ArtifactReference.builder()
                .globalId(this.getGlobalId())
                .contentId(this.getContentId())
                .contentHash(this.getContentHash())
                .groupId(this.getGroupId())
                .artifactId(this.getArtifactId())
                .version(this.getVersion())
                .build();
    }

    public ArtifactCoordinates toArtifactCoordinates() {
        return ArtifactCoordinates.builder()
                .groupId(this.getGroupId())
                .artifactId(this.getArtifactId())
                .version(this.getVersion())
                .build();
    }
}
