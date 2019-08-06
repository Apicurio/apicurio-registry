package io.apicurio.registry.storage.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents an <em>artifact version</em>.
 * <p>
 * Related data that are not always accessed together with the content should be stored separately
 * in {@link io.apicurio.registry.storage.MetaDataStorage}.
 * <p>
 * Equality testing is based on both {@link ArtifactVersion#getId()} and {@link ArtifactVersion#getGlobalId()} which,
 * since this is an immutable object, MUST be either both non-null or both null.
 * If one of the ID values is the same in the second instance, the other ID value MUST also match,
 * or there is an error somewhere.
 * <p>
 * MUST be immutable.
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class ArtifactVersion {

    @Include
    private ArtifactVersionId id;

    /**
     * Globally unique ID:(
     * Thanks Confluent...
     */
    @Include
    private Long globalId;

    private String content;

    public ArtifactVersion(String content) {
        this.content = content;
    }
}
