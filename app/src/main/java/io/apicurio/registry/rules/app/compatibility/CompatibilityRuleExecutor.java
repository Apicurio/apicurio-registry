package io.apicurio.registry.rules.app.compatibility;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.compatibility.CompatibilityCheckNotSupportedException;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_TYPES;
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
    Logger log;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @ConfigProperty(name = "apicurio.compat.allow-unsupported-types.enabled", defaultValue = "false")
    @Info(category = CATEGORY_TYPES, description = "Allow the COMPATIBILITY rule to pass silently for artifact types that have no compatibility checker implementation. When false (the default), enforcing a non-NONE compatibility level on such a type is rejected rather than reported as compatible.", availableSince = "3.3.2")
    boolean allowUnsupportedTypes;

    /**
     * @see io.apicurio.registry.rules.RuleExecutor#execute(io.apicurio.registry.rules.RuleContext)
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {
        CompatibilityLevel level = CompatibilityLevel.valueOf(context.getConfiguration());

        // If the compatibility level is NONE, the rule is disabled - do not execute
        if (level == CompatibilityLevel.NONE) {
            return;
        }

        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(context.getArtifactType());
        CompatibilityChecker checker = provider.getCompatibilityChecker();

        // A stub checker always answers "compatible" without comparing anything, so letting it through
        // would silently approve a breaking change. Fail closed unless the operator has explicitly
        // opted back into the previous (unchecked) behavior.
        if (!checker.isCompatibilitySupported()) {
            if (!allowUnsupportedTypes) {
                throw new CompatibilityCheckNotSupportedException(String.format(
                        "Compatibility level '%s' cannot be enforced for artifact '%s' because artifact type"
                                + " '%s' has no compatibility checker implementation. Set the COMPATIBILITY"
                                + " rule to NONE, remove it, or set"
                                + " apicurio.compat.allow-unsupported-types.enabled=true to restore the"
                                + " previous behavior of skipping the check.",
                        level, context.getArtifactId(), context.getArtifactType()));
            }
            log.warn("COMPATIBILITY rule level '{}' is NOT being enforced for artifact '{}' [{}]: this"
                    + " artifact type has no compatibility checker implementation and"
                    + " apicurio.compat.allow-unsupported-types.enabled is true.", level,
                    context.getArtifactId(), context.getArtifactType());
            return;
        }

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
