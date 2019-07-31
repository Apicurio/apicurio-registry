package io.apicurio.registry.storage.model;

public enum RuleType {

    NOOP_RULE("NOOP_RULE");

    RuleType(String type) {
        this.type = type;
    }

    private String type;

    public String getType() {
        return type;
    }
}
