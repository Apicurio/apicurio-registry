package io.apicurio.registry.storage.impl.search;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * CDI event fired when an entire artifact (all versions) is deleted.
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class ArtifactDeletedEvent {

    private final String groupId;
    private final String artifactId;
}
