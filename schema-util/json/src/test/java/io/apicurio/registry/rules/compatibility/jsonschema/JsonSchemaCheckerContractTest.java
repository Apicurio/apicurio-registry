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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     * The two halves use identical schemas and differ only in what the map holds, so the verdict
     * can only have come from the map. Asserting the incompatible half on its own would also be
     * satisfied by an implementation that rejects every external {@code $ref} without resolving
     * anything.
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

        var matching = checker.testCompatibility(CompatibilityLevel.BACKWARD, List.of(json(existing)),
                json(proposed), Collections.singletonMap("address.json", json("""
                        { "type": "integer" }
                        """)));

        assertTrue(matching.isCompatible(),
                "Resolving to the type the property already had leaves nothing incompatible");

        var differing = checker.testCompatibility(CompatibilityLevel.BACKWARD, List.of(json(existing)),
                json(proposed), Collections.singletonMap("address.json", json("""
                        { "type": "string" }
                        """)));

        assertFalse(differing.isCompatible(),
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
     * Every difference becomes a {@code RuleViolation} in the API response, which is what a user
     * whose upload was rejected reads, so both fields have to be populated whichever checker
     * produced it.
     * <p>
     * Both checkers report a change inside a nested schema at the keyword that changed, as a JSON
     * Pointer into the schema, so the context is asserted exactly. The descriptions are worded
     * differently — the legacy checker says {@code "String type max length decreased"}, Data Models
     * {@code "The 'maxLength' string-length limit was decreased."} — so what is asserted is that
     * the description is a sentence rather than a constant name such as
     * {@code STRING_TYPE_MAX_LENGTH_DECREASED}, which the Apitomy adapter used to report.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("checkers")
    void violationsCarryADescriptionAndAContext(CompatibilityChecker checker) {
        String existing = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {"name": {"type": "string", "maxLength": 10}}
                }
                """;
        String proposed = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {"name": {"type": "string", "maxLength": 5}}
                }
                """;

        var result = checker.testCompatibility(CompatibilityLevel.BACKWARD, List.of(json(existing)),
                json(proposed), Map.of());

        assertFalse(result.isCompatible(), "Decreasing maxLength is not backward compatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                () -> "One edit, one violation: " + result.getIncompatibleDifferences());

        var violation = result.getIncompatibleDifferences().iterator().next().asRuleViolation();
        var description = violation.getDescription();
        assertFalse(description == null || description.isBlank(), "Every violation needs a description");
        assertTrue(description.contains(" ") && !description.contains("_"),
                () -> "The description is prose for the user, not a constant name: " + description);
        assertEquals("/properties/name/maxLength", violation.getContext(),
                "The context points at the keyword that changed");
    }
}
