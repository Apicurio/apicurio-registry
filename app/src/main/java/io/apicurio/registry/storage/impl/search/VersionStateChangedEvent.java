package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.types.VersionState;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * CDI event fired when an artifact version state changes (e.g., ENABLED -> DISABLED).
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class VersionStateChangedEvent {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final long globalId;
    private final VersionState oldState;
    private final VersionState newState;
}
