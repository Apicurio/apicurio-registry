package io.apicurio.registry.storage.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Getter;
import lombok.ToString;

/**
 * A piece of meta-data about an <em>artifact</em> or an <em>artifact version</em>.
 * <p>
 * Can be marked read-only.
 * <p>
 * MUST be immutable.
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class MetaValue {



    @Include
    private String key;

    private String value;

    private boolean readOnly;
}
