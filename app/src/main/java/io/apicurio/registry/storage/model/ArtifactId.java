package io.apicurio.registry.storage.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;

/**
 * Uniquely Identifies an artifact
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class ArtifactId extends ArtifactSequenceId {

    public ArtifactId(String sequence, long version) {
        super(sequence);
        this.version = version;
    }

    /**
     * Globally unique ID:(
     * Thanks Confluent...
     */
    @Include
    public Long id;

    /**
     * Version ID, that together with sequence ID is also globally unique:
     * < sequence, version>
     */
    @Include
    private Long version;
}
