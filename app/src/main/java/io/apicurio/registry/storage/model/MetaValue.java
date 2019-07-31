package io.apicurio.registry.storage.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;

/**
 * a piece of metadata about an artifact or sequence.
 * Can be marked read-only. TODO who enforces that?
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MetaValue {

    @Include
    private String key;

    private String content;

    private boolean readOnly;
}
