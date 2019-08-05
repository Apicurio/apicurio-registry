package io.apicurio.registry.storage.model;

/**
 * A list of known types of <em>rules</em>.
 */
public enum RuleType {

    NOOP("NOOP"),
    COMPATIBILITY("COMPATIBILITY"),
    VALIDATION("VALIDATION"),
    CONFORMANCE("CONFORMANCE");

    RuleType(String typeValue) {
        this.typeValue = typeValue;
    }

    private String typeValue;

    public String getTypeValue() {
        return typeValue;
    }
}
