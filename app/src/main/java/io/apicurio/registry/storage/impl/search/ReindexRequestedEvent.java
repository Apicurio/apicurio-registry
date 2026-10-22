package io.apicurio.registry.storage.impl.search;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * CDI event fired when a full reindex of the search index is requested,
 * typically after a data import or upgrade operation.
 */
@EqualsAndHashCode
@ToString
public class ReindexRequestedEvent {
}
