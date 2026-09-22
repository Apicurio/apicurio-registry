package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.json.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compatibility levels other than BACKWARD, checked against real schemas.
 * <p>
 * {@code AbstractCompatibilityChecker} derives every level from the single
 * {@code isBackwardsCompatibleWith} primitive — FORWARD by swapping its arguments, FULL by unioning
 * both directions, and the transitive variants by folding over the version list. Only BACKWARD with
 * a single existing version was exercised for JSON Schema, so whether the derived levels produce
 * the right verdict on real schemas was untested.
 * <p>
 * The routing and de-duplication of that derivation is covered generically by
 * {@code AbstractCompatibilityCheckerTest} with a stub checker, and is not repeated here. What this
 * adds is the other half: that the verdicts are correct for schemas a user would actually write.
 * <p>
 * Every case runs against both checkers. Where they disagree the failure is informative either way
 * — a regression in the new checker, or a defect in the legacy one that has been shipping
 * unobserved.
 */
public class JsonSchemaCompatibilityLevelTest {

    static Stream<CompatibilityChecker> checkers() {
        return Stream.of(new JsonSchemaCompatibilityChecker(), new ApitomyJsonSchemaCompatibilityChecker());
    }

    /** Accepts strings of up to 10 characters. */
    private static final String WIDE = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "string",
              "maxLength": 10
            }
            """;

    /** Accepts strings of up to 5 characters, so strictly fewer instances than {@link #WIDE}. */
    private static final String NARROW = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "string",
              "maxLength": 5
            }
            """;

    private static final String ENUM_ABC = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "enum": ["a", "b", "c"]
            }
            """;

    private static final String ENUM_AB = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "enum": ["a", "b"]
            }
            """;

    private static final String ENUM_ABD = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "enum": ["a", "b", "d"]
            }
            """;

    private static TypedContent content(String schema) {
        return TypedContent.create(ContentHandle.create(schema), ContentTypes.APPLICATION_JSON);
    }

    private static boolean check(CompatibilityChecker checker, CompatibilityLevel level,
            List<String> existing, String proposed) {
        return checker.testCompatibility(level,
                existing.stream().map(JsonSchemaCompatibilityLevelTest::content).toList(),
                content(proposed), Collections.emptyMap()).isCompatible();
    }

    /**
     * Widening a constraint is backward compatible: everything the old schema accepted, the new one
     * still accepts. It is not forward compatible, because the old schema rejects the longer strings
     * the new one now permits.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void wideningIsBackwardButNotForward(CompatibilityChecker checker) {
        assertTrue(check(checker, CompatibilityLevel.BACKWARD, List.of(NARROW), WIDE),
                "Widening maxLength should be backward compatible");
        assertFalse(check(checker, CompatibilityLevel.FORWARD, List.of(NARROW), WIDE),
                "Widening maxLength should not be forward compatible");
        assertFalse(check(checker, CompatibilityLevel.FULL, List.of(NARROW), WIDE),
                "FULL requires both directions, and forward fails here");
    }

    /**
     * The mirror image: narrowing is forward compatible but not backward. Together with the previous
     * case this pins the direction of the argument swap — a checker that confused the two would pass
     * one of these and fail the other.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void narrowingIsForwardButNotBackward(CompatibilityChecker checker) {
        assertFalse(check(checker, CompatibilityLevel.BACKWARD, List.of(WIDE), NARROW),
                "Narrowing maxLength should not be backward compatible");
        assertTrue(check(checker, CompatibilityLevel.FORWARD, List.of(WIDE), NARROW),
                "Narrowing maxLength should be forward compatible");
        assertFalse(check(checker, CompatibilityLevel.FULL, List.of(WIDE), NARROW),
                "FULL requires both directions, and backward fails here");
    }

    /** An unchanged schema is compatible in every direction. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void identicalSchemaIsFullyCompatible(CompatibilityChecker checker) {
        assertTrue(check(checker, CompatibilityLevel.BACKWARD, List.of(WIDE), WIDE));
        assertTrue(check(checker, CompatibilityLevel.FORWARD, List.of(WIDE), WIDE));
        assertTrue(check(checker, CompatibilityLevel.FULL, List.of(WIDE), WIDE));
    }

    /**
     * The case that distinguishes a transitive level from its non-transitive counterpart.
     * <p>
     * Against the newest version alone the proposal only adds an enum member, which is safe. Against
     * the version before it the proposal also drops {@code "c"}, which is not. A non-transitive
     * check sees only the former.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void transitiveSeesOlderVersionsThatNonTransitiveMisses(CompatibilityChecker checker) {
        List<String> versions = List.of(ENUM_ABC, ENUM_AB); // newest last

        assertTrue(check(checker, CompatibilityLevel.BACKWARD, versions, ENUM_ABD),
                "Against the newest version this only adds an enum member");
        assertFalse(check(checker, CompatibilityLevel.BACKWARD_TRANSITIVE, versions, ENUM_ABD),
                "Against the oldest version it also removes enum member 'c'");
    }

    /** FULL_TRANSITIVE must fail when either direction fails against any existing version. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void fullTransitiveUnionsBothDirectionsAcrossAllVersions(CompatibilityChecker checker) {
        assertFalse(check(checker, CompatibilityLevel.FULL_TRANSITIVE, List.of(ENUM_ABC, ENUM_AB),
                ENUM_ABD), "FULL_TRANSITIVE should fail when any version fails in any direction");
    }

    /** Transitive levels degenerate to their non-transitive form for a single existing version. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void transitiveMatchesNonTransitiveForASingleVersion(CompatibilityChecker checker) {
        assertTrue(check(checker, CompatibilityLevel.BACKWARD_TRANSITIVE, List.of(NARROW), WIDE));
        assertFalse(check(checker, CompatibilityLevel.FORWARD_TRANSITIVE, List.of(NARROW), WIDE));
    }
}
