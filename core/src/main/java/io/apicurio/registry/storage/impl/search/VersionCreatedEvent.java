package io.apicurio.registry.storage.impl.search;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * CDI event fired when a new artifact version is created.
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class VersionCreatedEvent {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final long globalId;
    private final long contentId;
}
