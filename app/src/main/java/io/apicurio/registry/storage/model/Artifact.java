package io.apicurio.registry.storage.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;

/**
 * Represents a stored artifact.
 * Meta data that should not be accessed every time with the content should be stored separately
 * in {@link io.apicurio.registry.storage.MetaData}
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Artifact {

    @Include
    private ArtifactId id;

    private String content;
}
