package io.apicurio.registry.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;

/**
 * Uniquely identifies a sequence
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ArtifactSequenceId {

    @Include
    private String sequence; // TODO rename for clarity, in API design it's `artifactId`
}
