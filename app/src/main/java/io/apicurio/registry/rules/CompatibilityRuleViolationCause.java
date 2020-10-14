package io.apicurio.registry.rules;

import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

public class CompatibilityRuleViolationCause extends RuleViolationCause {

    public CompatibilityRuleViolationCause(String cause, Difference incompatibleDiff) {
        super(cause);
        this.incompatibleDiff = incompatibleDiff;
    }

    @Getter
    private Difference incompatibleDiff;

    public static Set<RuleViolationCause> transformCompatibilitySet(Set<Difference> incompatibleDiffs) {
        Set<RuleViolationCause> ruleViolationCauses = new HashSet<>();

        for (Difference diff : incompatibleDiffs) {
            ruleViolationCauses.add(new CompatibilityRuleViolationCause(
                    diff.getDiffType().toString(), diff
            ));
        }

        return ruleViolationCauses;
    }
}
