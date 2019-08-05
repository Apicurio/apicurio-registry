package io.apicurio.registry.storage.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Getter;
import lombok.ToString;

/**
 * Uniquely identifies an <em>artifact</em>,
 * which is a sequence of {@link io.apicurio.registry.storage.model.ArtifactVersion}.
 * <p>
 * MUST be immutable.
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class ArtifactId {

    @Include
    private String artifactId;
}
