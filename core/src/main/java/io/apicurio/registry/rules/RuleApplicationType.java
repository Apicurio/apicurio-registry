package io.apicurio.registry.rules;

/**
 * Indicates the context in which a rule is being applied, so that rule implementations can adjust their
 * behavior accordingly.
 */
public enum RuleApplicationType {

    /**
     * The rule is being applied during creation of a new artifact version (first version of a new artifact).
     */
    CREATE,

    /**
     * The rule is being applied during an update to an existing artifact (adding a new version).
     */
    UPDATE;

}
