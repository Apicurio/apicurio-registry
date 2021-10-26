package io.apicurio.registry.rest.v1.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by aohana
 */
public class RuleViolationCause {

    public RuleViolationCause() {
    }

    public RuleViolationCause(String description, String context) {
        this.description = description;
        this.context = context;
    }

    @JsonProperty("description")
    private String description;

    @JsonProperty("context")
    private String context;

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("context")
    public String getContext() {
        return context;
    }

    @JsonProperty("context")
    public void setContext(String context) {
        this.context = context;
    }

    @Override
    public String toString() {
        return "RuleViolationCause{" +
                "description='" + description + '\'' +
                ", context='" + context + '\'' +
                '}';
    }
}
