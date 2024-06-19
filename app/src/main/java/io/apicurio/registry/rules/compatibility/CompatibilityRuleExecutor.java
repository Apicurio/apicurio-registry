package io.apicurio.registry.rules.compatibility;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

/**
 * Rule executor for the "Compatibility" rule. The Compatibility Rule is responsible for ensuring that the
 * updated content does not violate the configured compatibility level. Levels include e.g. Backward
 * compatibility.
 */
@ApplicationScoped
@Logged
public class CompatibilityRuleExecutor implements RuleExecutor {

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    /**
     * @see io.apicurio.registry.rules.RuleExecutor#execute(io.apicurio.registry.rules.RuleContext)
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {
        CompatibilityLevel level = CompatibilityLevel.valueOf(context.getConfiguration());
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(context.getArtifactType());
        CompatibilityChecker checker = provider.getCompatibilityChecker();
        List<TypedContent> existingArtifacts = context.getCurrentContent() != null
            ? context.getCurrentContent() : emptyList();
        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(level,
                existingArtifacts, context.getUpdatedContent(), context.getResolvedReferences());
        if (!compatibilityExecutionResult.isCompatible()) {
            throw new RuleViolationException(String.format(
                    "Incompatible artifact: %s [%s], num of incompatible diffs: {%s}, list of diff types: %s",
                    context.getArtifactId(), context.getArtifactType(),
                    compatibilityExecutionResult.getIncompatibleDifferences().size(),
                    outputReadableCompatabilityDiffs(
                            compatibilityExecutionResult.getIncompatibleDifferences())),
                    RuleType.COMPATIBILITY, context.getConfiguration(),
                    transformCompatibilityDiffs(compatibilityExecutionResult.getIncompatibleDifferences()));
        }
    }

    /**
     * Convert the set of compatibility differences into a collection of rule violation causes for return to
     * the user.
     * 
     * @param differences
     */
    private Set<RuleViolation> transformCompatibilityDiffs(Set<CompatibilityDifference> differences) {
        if (!differences.isEmpty()) {
            Set<RuleViolation> res = new HashSet<>();
            for (CompatibilityDifference diff : differences) {
                res.add(diff.asRuleViolation());
            }
            return res;
        } else {
            return Collections.emptySet();
        }
    }

    private List<String> outputReadableCompatabilityDiffs(Set<CompatibilityDifference> differences) {
        if (!differences.isEmpty()) {
            List<String> res = new ArrayList<String>();
            for (CompatibilityDifference diff : differences) {
                res.add(diff.asRuleViolation().getDescription() + " at "
                        + diff.asRuleViolation().getContext());
            }
            return res;
        } else {
            return new ArrayList<String>();
        }
    }

}
