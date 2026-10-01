package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a checker behaves as Registry actually calls it: content arriving as {@link TypedContent},
 * references pre-resolved from storage into a map, and differences surfaced as rule violations.
 * <p>
 * None of this is reachable from the schema catalogue, which deals only in schema pairs.
 */
public class JsonSchemaCheckerContractTest {

    private final CompatibilityChecker checker = new ApitomyJsonSchemaCompatibilityChecker();

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
    @Test
    void referencesSuppliedByRegistryAreResolved() {
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
    @Test
    void yamlContentIsRejectedRatherThanMisCompared() {
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
     * whose upload was rejected reads, so both fields have to be populated.
     * <p>
     * A change inside a nested schema is reported at the keyword that changed, as a JSON Pointer
     * into the schema. The description is a sentence, such as {@code "The 'maxLength'
     * string-length limit was decreased."}, rather than a constant name such as
     * {@code STRING_TYPE_MAX_LENGTH_DECREASED}, which the adapter used to report.
     */
    @Test
    void violationsCarryADescriptionAndAContext() {
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
