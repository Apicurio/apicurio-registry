package io.apicurio.registry.references.auto;

import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thrown when one or more references could not be automatically matched to existing artifacts.
 */
public class ReferenceAutoMatchException extends RuleViolationException {

    private static final long serialVersionUID = 1L;

    public static final String TITLE = "Some references could not be matched to existing artifacts.";

    /**
     * Constructor.
     *
     * @param problems the match problems, reported as causes sorted by reference
     */
    public ReferenceAutoMatchException(List<MatchProblem> problems) {
        super(TITLE, RuleType.INTEGRITY, null, toCauses(problems));
    }

    private static Set<RuleViolation> toCauses(List<MatchProblem> problems) {
        return problems.stream()
                .sorted(Comparator.comparing(MatchProblem::reference))
                .map(p -> new RuleViolation(p.description(), p.reference()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
