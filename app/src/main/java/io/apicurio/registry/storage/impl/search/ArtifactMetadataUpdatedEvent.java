package io.apicurio.registry.storage.impl.search;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * CDI event fired when artifact metadata is updated (name, description, labels, etc.). This affects all
 * versions of the artifact.
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class ArtifactMetadataUpdatedEvent {

    private final String groupId;
    private final String artifactId;
}
