package io.apicurio.registry.rest.v1.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Set;

/**
 * Created by aohana
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "causes",
})
public class RuleViolationError extends Error {

    @JsonProperty("causes")
    private Set<RuleViolationCause> causes;

    @JsonProperty("causes")
    public Set<RuleViolationCause> getCauses() {
        return causes;
    }

    @JsonProperty("causes")
    public void setCauses(Set<RuleViolationCause> causes) {
        this.causes = causes;
    }

    @Override
    public String toString() {
        return "RuleViolationError{" +
                "causes=" + causes +
                '}';
    }
}
