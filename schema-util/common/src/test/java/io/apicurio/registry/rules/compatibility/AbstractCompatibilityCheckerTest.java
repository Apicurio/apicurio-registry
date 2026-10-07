package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class AbstractCompatibilityCheckerTest {

    /**
     * A minimal checker that reports a difference with a direction-independent description whenever the
     * existing and proposed content are not equal (similar to how parse errors are reported by real
     * checkers).
     */
    private static final TestCompatibilityChecker SYMMETRIC_CHECKER = new TestCompatibilityChecker(
            (existing, proposed) -> existing.equals(proposed) ? Collections.emptySet()
                : Set.of(new SimpleCompatibilityDifference("Content differs", "/")));

    /**
     * A minimal checker that reports a difference with a direction-dependent description whenever the
     * existing and proposed content are not equal.
     */
    private static final TestCompatibilityChecker DIRECTIONAL_CHECKER = new TestCompatibilityChecker(
            (existing, proposed) -> existing.equals(proposed) ? Collections.emptySet()
                : Set.of(new SimpleCompatibilityDifference(
                        "Changed from '" + existing + "' to '" + proposed + "'", "/")));

    @Test
    public void testCompatibleWhenNoDifferences() {
        CompatibilityExecutionResult result = SYMMETRIC_CHECKER.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(content("a")), content("a"), Collections.emptyMap());
        Assertions.assertTrue(result.isCompatible());
        Assertions.assertTrue(result.getIncompatibleDifferences().isEmpty());
    }

    @Test
    public void testCompatibleWhenNoExistingArtifacts() {
        CompatibilityExecutionResult result = SYMMETRIC_CHECKER.testCompatibility(
                CompatibilityLevel.BACKWARD, Collections.emptyList(), content("a"),
                Collections.emptyMap());
        Assertions.assertTrue(result.isCompatible());
    }

    @Test
    public void testIncompatibleDifferencesAreReported() {
        CompatibilityExecutionResult result = SYMMETRIC_CHECKER.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(content("a")), content("b"), Collections.emptyMap());
        Assertions.assertFalse(result.isCompatible());
        Assertions.assertEquals(1, result.getIncompatibleDifferences().size());
        Assertions.assertEquals("Content differs",
                result.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription());
    }

    @Test
    public void testFullDeduplicatesEqualDifferences() {
        // FULL checks both directions. Both runs report an equal difference, which must be
        // deduplicated into a single reported difference.
        CompatibilityExecutionResult result = SYMMETRIC_CHECKER.testCompatibility(CompatibilityLevel.FULL,
                List.of(content("a")), content("b"), Collections.emptyMap());
        Assertions.assertFalse(result.isCompatible());
        Assertions.assertEquals(1, result.getIncompatibleDifferences().size());
    }

    @Test
    public void testFullKeepsDistinctDifferences() {
        // FULL checks both directions. Each run reports a difference with a different description,
        // so both must be kept.
        CompatibilityExecutionResult result = DIRECTIONAL_CHECKER.testCompatibility(CompatibilityLevel.FULL,
                List.of(content("a")), content("b"), Collections.emptyMap());
        Assertions.assertFalse(result.isCompatible());
        Assertions.assertEquals(2, result.getIncompatibleDifferences().size());
    }

    @Test
    public void testFullTransitiveDeduplicatesAcrossVersions() {
        // FULL_TRANSITIVE checks both directions against every existing version. All runs report an
        // equal difference, which must be deduplicated into a single reported difference.
        CompatibilityExecutionResult result = SYMMETRIC_CHECKER.testCompatibility(
                CompatibilityLevel.FULL_TRANSITIVE, List.of(content("a"), content("c")), content("b"),
                Collections.emptyMap());
        Assertions.assertFalse(result.isCompatible());
        Assertions.assertEquals(1, result.getIncompatibleDifferences().size());
    }

    private static TypedContent content(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
    }

    private static class TestCompatibilityChecker
            extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

        private final BiFunction<String, String, Set<SimpleCompatibilityDifference>> check;

        private TestCompatibilityChecker(
                BiFunction<String, String, Set<SimpleCompatibilityDifference>> check) {
            this.check = check;
        }

        @Override
        protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing,
                String proposed, Map<String, TypedContent> resolvedReferences) {
            return check.apply(existing, proposed);
        }
    }
}
