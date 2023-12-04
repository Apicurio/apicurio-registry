package io.apicurio.registry.rules;

/**
 * This interface is used to execute/apply a specific rule.  Each rule supported by
 * the registry will have an implementation of this interface, where the logic specific
 * to the rule is applied.  For example, the Validity rule will have an implementation.
 */
public interface RuleExecutor {
    
    /**
     * Executes the logic of the rule against the given context.  The context
     * contains all data and meta-data necessary to execute the rule logic.
     * @param context
     * @throws RuleViolationException
     */
    public void execute(RuleContext context) throws RuleViolationException;

}
