package io.apicurio.registry.rules;

import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.RuleType;
import lombok.Getter;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Exception thrown when a configured rule is violated, rejecting an artifact content
 * update.
 *
 */
public class RuleViolationException extends RegistryException {

    private static final long serialVersionUID = 8437151164241883773L;

    @Getter
    private final RuleType ruleType;

    @Getter
    private final Optional<String> ruleConfiguration;

    @Getter
    private final Set<RuleViolation> causes;

    /**
     * Constructor.
     *
     * @param message
     * @param ruleType
     * @param ruleConfiguration
     * @param cause
     */
    public RuleViolationException(String message, RuleType ruleType, String ruleConfiguration, Throwable cause) {
        super(message, cause);
        this.ruleType = ruleType;
        this.ruleConfiguration = Optional.ofNullable(ruleConfiguration);
        this.causes = new HashSet<>();
    }

    /**
     * Constructor.
     *
     * @param message
     * @param ruleType
     * @param ruleConfiguration
     * @param causes
     */
    public RuleViolationException(String message, RuleType ruleType, String ruleConfiguration, Set<RuleViolation> causes) {
        super(message);
        this.ruleType = ruleType;
        this.ruleConfiguration = Optional.ofNullable(ruleConfiguration);
        this.causes = causes;
    }

    /**
     * Constructor.
     *
     * @param message
     * @param ruleType
     * @param ruleConfiguration
     * @param causes
     * @param cause
     */
    public RuleViolationException(String message, RuleType ruleType, String ruleConfiguration,
                                  Set<RuleViolation> causes, Throwable cause) {
        super(message, cause);
        this.ruleType = ruleType;
        this.ruleConfiguration = Optional.ofNullable(ruleConfiguration);
        this.causes = causes;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + causes.stream()
                .map(rv -> rv.getDescription() + (rv.getContext() != null && !rv.getContext().isBlank() ? " at " + rv.getContext() : ""))
                .reduce((left, right) -> left + ", " + right)
                .map(s -> " Causes: " + s).orElse("");
    }
}
