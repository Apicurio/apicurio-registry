package io.apicurio.registry.storage.model;

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
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@ToString
public class ArtifactVersionId extends ArtifactId {

    public ArtifactVersionId(String artifactId, Long versionId) {
        super(artifactId);
        this.versionId = versionId;
    }

    @Include
    private Long versionId;
}
