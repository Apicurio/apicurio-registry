package io.apicurio.registry.storage.impl.search;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * CDI event fired when an artifact version is deleted.
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class VersionDeletedEvent {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final long globalId;
}
