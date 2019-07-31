package io.apicurio.registry.storage.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an instance of the specified rule `type`.
 * Instance can be assigned to artifact or sequence
 * and optionally configured.
 */
@Data
@EqualsAndHashCode
public class RuleInstance {

    @Include
    private RuleType type;

    private boolean enabled;

    private Map<String, String> config = new ConcurrentHashMap<>();
}
