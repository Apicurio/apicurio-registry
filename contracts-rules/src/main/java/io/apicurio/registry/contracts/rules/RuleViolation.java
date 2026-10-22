package io.apicurio.registry.contracts.rules;

public class RuleViolation {
    private String ruleName;
    private String message;
    private String action;

    public RuleViolation() {
    }

    public RuleViolation(String ruleName, String message, String action) {
        this.ruleName = ruleName;
        this.message = message;
        this.action = action;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
