package io.apicurio.registry.rules;

import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

public class CompatibilityRuleViolation extends RuleViolation {

    public CompatibilityRuleViolation(String cause, Difference incompatibleDiff) {
        super(cause);
        this.incompatibleDiff = incompatibleDiff;
    }

    @Getter
    private Difference incompatibleDiff;

    public static Set<RuleViolation> transformCompatibilitySet(Set<Difference> incompatibleDiffs) {
        Set<RuleViolation> ruleViolations = new HashSet<>();

        for (Difference diff : incompatibleDiffs) {
            ruleViolations.add(new CompatibilityRuleViolation(
                    diff.getDiffType().toString(), diff
            ));
        }

        return ruleViolations;
    }
}
