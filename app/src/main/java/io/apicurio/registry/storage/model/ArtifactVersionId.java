package io.apicurio.registry.storage.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Getter;
import lombok.ToString;

/**
 * A tuple of <pre><code>[artifact ID, version ID]</code></pre> which uniquely identifies
 * an {@link io.apicurio.registry.storage.model.ArtifactVersion}.
 * <p>
 * There is an other way to uniquely identify an {@link io.apicurio.registry.storage.model.ArtifactVersion}:
 * {@link ArtifactVersion#getGlobalId()}.
 * <p>
 * MUST be immutable.
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class ArtifactVersionId {

    @Include
    private String artifactId;

    @Include
    private Long versionId;

    public ArtifactId asArtifactId() {
        return new ArtifactId(artifactId);
    }
}
