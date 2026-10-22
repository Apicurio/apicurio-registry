package io.apicurio.registry.storage.impl.search;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * CDI event fired when all artifacts in a group are deleted.
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class GroupDeletedEvent {

    private final String groupId;
}
