package io.apicurio.registry.rules;

import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.RuleType;
import lombok.Getter;

/**
 * Exception thrown when attempting to delete a configured default rule.
 */
public class DefaultRuleDeletionException extends RegistryException {

    private static final long serialVersionUID = 1599508405950159L;

    @Getter
    private final RuleType ruleType;

    /**
     * Constructor.
     */
    public DefaultRuleDeletionException(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "Default rule '" + this.ruleType.name() + "' cannot be deleted.";
    }
}
