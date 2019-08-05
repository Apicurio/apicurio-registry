package io.apicurio.registry.storage.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * Represents an instance of the specified {@link io.apicurio.registry.storage.model.RuleType}.
 * <p>
 * Instances can be assigned to an <em>artifact</em> in {@link io.apicurio.registry.storage.ArtifactStorage}
 * or an <em>artifact version</em> in {@link io.apicurio.registry.storage.ArtifactVersionStorage}.
 * <p>
 * Each rule instance can be configured individually.
 * <p>
 * MUST be immutable (even the config map!).
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class RuleInstance {

    @Include
    private RuleType type;

    private Map<String, String> config;

    private boolean enabled;
}
