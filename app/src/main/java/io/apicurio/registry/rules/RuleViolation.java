package io.apicurio.registry.rules;

import com.google.common.collect.ImmutableSet;
import io.apicurio.registry.rules.validity.InvalidContentException;
import lombok.Getter;

import java.util.Set;

public class RuleViolation {

    public RuleViolation(String cause) {
        this.cause = cause;
    }

    @Getter
    private String cause;

    public static Set<RuleViolation> transformValiditySet(InvalidContentException invalidContentException) {
        return ImmutableSet.of(new RuleViolation(invalidContentException.getCause().toString()));
    }
}
