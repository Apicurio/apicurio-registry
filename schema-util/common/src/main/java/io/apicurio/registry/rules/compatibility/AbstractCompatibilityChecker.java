package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

public abstract class AbstractCompatibilityChecker<D extends CompatibilityDifference>
        implements CompatibilityChecker {

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel,
            List<TypedContent> existingArtifacts, TypedContent proposedArtifact,
            Map<String, TypedContent> resolvedReferences) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingSchemas MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedSchema MUST NOT be null");

        if (existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        final String proposedArtifactContent = proposedArtifact.getContent().content();

        Set<D> incompatibleDiffs = new HashSet<>();
        String lastExistingSchema = existingArtifacts.get(existingArtifacts.size() - 1).getContent()
                .content();

        switch (compatibilityLevel) {
            case BACKWARD:
                incompatibleDiffs = isBackwardsCompatibleWith(lastExistingSchema, proposedArtifactContent,
                        resolvedReferences);
                break;
            case BACKWARD_TRANSITIVE:
                incompatibleDiffs = transitively(existingArtifacts, proposedArtifactContent, (existing,
                        proposed) -> isBackwardsCompatibleWith(existing, proposed, resolvedReferences));
                break;
            case FORWARD:
                incompatibleDiffs = isBackwardsCompatibleWith(proposedArtifactContent, lastExistingSchema,
                        resolvedReferences);
                break;
            case FORWARD_TRANSITIVE:
                incompatibleDiffs = transitively(existingArtifacts, proposedArtifactContent, (existing,
                        proposed) -> isBackwardsCompatibleWith(proposed, existing, resolvedReferences));
                break;
            case FULL:
                incompatibleDiffs = unionOf(
                        isBackwardsCompatibleWith(lastExistingSchema, proposedArtifactContent,
                                resolvedReferences),
                        isBackwardsCompatibleWith(proposedArtifactContent, lastExistingSchema,
                                resolvedReferences));
                break;
            case FULL_TRANSITIVE:
                incompatibleDiffs = unionOf(
                        transitively(existingArtifacts, proposedArtifactContent,
                                (existing, proposed) -> isBackwardsCompatibleWith(existing, proposed,
                                        resolvedReferences)),
                        transitively(existingArtifacts, proposedArtifactContent,
                                (existing, proposed) -> isBackwardsCompatibleWith(proposed, existing,
                                        resolvedReferences)));
                break;
            case NONE:
                break;
        }
        return CompatibilityExecutionResult.incompatibleOrEmpty(new HashSet<>(incompatibleDiffs));
    }

    /**
     * Given a proposed schema, walk the existing schemas in reverse order (i.e. newest to oldest), and for
     * each pair (existing, proposed) call the check function.
     *
     * @return The collected set of differences.
     */
    private Set<D> transitively(List<TypedContent> existingSchemas, String proposedSchema,
            BiFunction<String, String, Set<D>> checkExistingProposed) {
        Set<D> result = new HashSet<>();
        for (int i = existingSchemas.size() - 1; i >= 0; i--) { // TODO This may become too slow, more wide
                                                                // refactoring needed.
            Set<D> current = checkExistingProposed.apply(existingSchemas.get(i).getContent().content(),
                    proposedSchema);
            result.addAll(current);
        }
        return result;
    }

    /**
     * Checks whether the proposed artifact content is backwards compatible with the existing artifact
     * content.
     * <p>
     * Implementations should distinguish between two situations:
     * <ul>
     * <li>The proposed content is not compatible with the existing content. This is a normal result of the
     * check, and should be reported by returning a non-empty set of differences.</li>
     * <li>Compatibility could not be determined at all (e.g. the content could not be parsed). This is a
     * genuine error, and should be reported by throwing an exception.</li>
     * </ul>
     * Note that the differences returned by this method are deduplicated using a {@link Set} (e.g. when
     * checking {@link CompatibilityLevel#FULL} compatibility), so implementations of
     * {@link CompatibilityDifference} must provide consistent equals and hashCode methods.
     *
     * @return the set of incompatible differences found. An empty set means the proposed content is
     *         compatible.
     */
    protected abstract Set<D> isBackwardsCompatibleWith(String existing, String proposed,
            Map<String, TypedContent> resolvedReferences);

    private Set<D> unionOf(Set<D>... from) {
        Set<D> rval = new HashSet<>();
        for (Set<D> f : from) {
            rval.addAll(f);
        }
        return rval;
    }

}
