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
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a checker behaves as Registry actually calls it: content arriving as {@link TypedContent},
 * references pre-resolved from storage into a map, and differences surfaced as rule violations.
 * <p>
 * None of this is reachable from the schema catalogue the checkers share, which deals only in
 * schema pairs. Every case runs against both checkers, so a difference between them shows up as a
 * failure rather than being discovered later.
 */
public class JsonSchemaCheckerContractTest {

    static Stream<CompatibilityChecker> checkers() {
        return Stream.of(new JsonSchemaCompatibilityChecker(), new ApitomyJsonSchemaCompatibilityChecker());
    }

    private static TypedContent json(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
    }

    private static TypedContent yaml(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_YAML);
    }

    /**
     * Registry resolves an artifact's references from its own storage and passes them in by name.
     * This is the only path through {@code RegistryResourceResolver}, and the catalogue cannot
     * reach it.
     * <p>
     * Replacing an {@code integer} property with a reference to a {@code string} schema is
     * incompatible, but only if the reference is actually followed — a checker ignoring the
     * supplied map would have nothing to compare and could not reach that verdict.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void referencesSuppliedByRegistryAreResolved(CompatibilityChecker checker) {
        String existing = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": { "x": { "type": "integer" } }
                }
                """;
        String proposed = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": { "x": { "$ref": "address.json" } }
                }
                """;

        var result = checker.testCompatibility(CompatibilityLevel.BACKWARD, List.of(json(existing)),
                json(proposed), Collections.singletonMap("address.json", json("""
                        { "type": "string" }
                        """)));

        assertFalse(result.isCompatible(),
                "The supplied reference should be followed, making integer -> string visible");
    }

    /**
     * Neither checker parses YAML: {@code AbstractCompatibilityChecker} hands the raw string to the
     * implementation and drops the content type, so a YAML schema reaches a JSON parser.
     * <p>
     * This pins the failure mode rather than the limitation. A YAML schema must be rejected
     * outright — the dangerous outcome would be parsing far enough to return a verdict, since
     * "compatible" from a schema that was never really read is worse than an error.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void yamlContentIsRejectedRatherThanMisCompared(CompatibilityChecker checker) {
        String existingYaml = """
                $schema: http://json-schema.org/draft-07/schema#
                type: string
                maxLength: 10
                """;
        String proposedYaml = """
                $schema: http://json-schema.org/draft-07/schema#
                type: string
                maxLength: 5
                """;

        assertThrows(RuntimeException.class,
                () -> checker.testCompatibility(CompatibilityLevel.BACKWARD,
                        List.of(yaml(existingYaml)), yaml(proposedYaml), Map.of()),
                "YAML should fail loudly rather than produce a verdict from an unparsed schema");
    }

    /**
     * Every difference becomes a {@code RuleViolation} in the API response, so both fields have to
     * be populated whichever checker produced it.
     * <p>
     * The two do not agree on what they contain — the legacy checker reports
     * {@code "String type max length decreased"} at {@code /maxLength}, the Apitomy adapter reports
     * {@code STRING_TYPE_MAX_LENGTH_DECREASED} at {@code /} — so this asserts only what a caller
     * can rely on from either. Narrowing that gap is tracked separately.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void violationsCarryADescriptionAndAContext(CompatibilityChecker checker) {
        String existing = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "string",
                  "maxLength": 10
                }
                """;
        String proposed = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "string",
                  "maxLength": 5
                }
                """;

        var result = checker.testCompatibility(CompatibilityLevel.BACKWARD, List.of(json(existing)),
                json(proposed), Map.of());

        assertFalse(result.isCompatible(), "Decreasing maxLength is not backward compatible");
        assertFalse(result.getIncompatibleDifferences().isEmpty(),
                "An incompatible result must say what was incompatible");

        result.getIncompatibleDifferences().forEach(difference -> {
            var violation = difference.asRuleViolation();
            assertFalse(violation.getDescription() == null || violation.getDescription().isBlank(),
                    "Every violation needs a description");
            assertTrue(violation.getContext() != null && violation.getContext().startsWith("/"),
                    "Every violation needs a context rooted at '/'");
        });
    }
}
