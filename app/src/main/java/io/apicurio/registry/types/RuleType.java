package io.apicurio.registry.types;

/**
 * @author Ales Justin
 */
public enum RuleType {
    compatibility,
    validation;

    public static RuleType fromString(String string) {
        for (RuleType type : values()) {
            if (string.equalsIgnoreCase(type.name())) {
                return type;
            }
        }
        throw new IllegalArgumentException("No such rule: " + string);
    }
}
