package io.apicurio.registry.avro.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityFixSuggestion;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AvroCompatibilityFixSuggestionsTest {

    private final AvroCompatibilityChecker checker = new AvroCompatibilityChecker();

    @Test
    public void missingDefaultFieldProducesRecommendedAndAcceptableSuggestions() {
        String existing = """
                {"type":"record","name":"Order","fields":[
                  {"name":"id","type":"string"}
                ]}
                """;
        String proposed = """
                {"type":"record","name":"Order","fields":[
                  {"name":"id","type":"string"},
                  {"name":"email","type":"string"}
                ]}
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(typed(existing)), typed(proposed), Map.of());

        assertFalse(result.isCompatible());
        RuleViolation violation = singleViolation(result);
        assertEquals("READER_FIELD_MISSING_DEFAULT_VALUE", violation.getType());
        assertTrue(violation.getDescription().contains("email"));
        assertTrue(violation.hasSuggestions());
        assertEquals(2, violation.getSuggestions().size());
        assertEquals(CompatibilityFixSuggestion.Tier.RECOMMENDED, violation.getSuggestions().get(0).getTier());
        assertEquals(CompatibilityFixSuggestion.Tier.ACCEPTABLE, violation.getSuggestions().get(1).getTier());
        assertTrue(violation.getSuggestions().get(0).getExample().contains("\"default\""));
        assertTrue(violation.getSuggestions().get(1).getExample().contains("[\"null\""));
    }

    @Test
    public void typeMismatchProducesWidenAndUnionSuggestions() {
        String existing = """
                {"type":"record","name":"Metrics","fields":[
                  {"name":"count","type":"long"}
                ]}
                """;
        String proposed = """
                {"type":"record","name":"Metrics","fields":[
                  {"name":"count","type":"int"}
                ]}
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(typed(existing)), typed(proposed), Map.of());

        assertFalse(result.isCompatible());
        RuleViolation violation = singleViolation(result);
        assertEquals("TYPE_MISMATCH", violation.getType());
        assertTrue(violation.hasSuggestions());
        assertTrue(violation.getSuggestions().stream()
                .anyMatch(s -> s.getTier() == CompatibilityFixSuggestion.Tier.RECOMMENDED));
        assertTrue(violation.getSuggestions().stream()
                .anyMatch(s -> s.getExample() != null && s.getExample().contains("long")));
    }

    @Test
    public void missingEnumSymbolProducesSuggestions() {
        String existing = """
                {"type":"record","name":"Event","fields":[
                  {"name":"status","type":{"type":"enum","name":"Status","symbols":["NEW","DONE"]}}
                ]}
                """;
        String proposed = """
                {"type":"record","name":"Event","fields":[
                  {"name":"status","type":{"type":"enum","name":"Status","symbols":["NEW"]}}
                ]}
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(typed(existing)), typed(proposed), Map.of());

        assertFalse(result.isCompatible());
        RuleViolation violation = singleViolation(result);
        assertEquals("MISSING_ENUM_SYMBOLS", violation.getType());
        assertTrue(violation.hasSuggestions());
        assertTrue(violation.getSuggestions().stream()
                .anyMatch(s -> s.getTier() == CompatibilityFixSuggestion.Tier.RECOMMENDED));
        assertTrue(violation.getSuggestions().stream()
                .anyMatch(s -> s.getTier() == CompatibilityFixSuggestion.Tier.WORKAROUND));
    }

    @Test
    public void compatibleChangeHasNoViolationsOrSuggestions() {
        String existing = """
                {"type":"record","name":"Order","fields":[
                  {"name":"id","type":"string"}
                ]}
                """;
        String proposed = """
                {"type":"record","name":"Order","fields":[
                  {"name":"id","type":"string"},
                  {"name":"note","type":"string","default":""}
                ]}
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(typed(existing)), typed(proposed), Map.of());

        assertTrue(result.isCompatible());
        assertTrue(result.getIncompatibleDifferences().isEmpty());
    }

    private static TypedContent typed(String schema) {
        return TypedContent.create(ContentHandle.create(schema), ContentTypes.APPLICATION_JSON);
    }

    private static RuleViolation singleViolation(CompatibilityExecutionResult result) {
        CompatibilityDifference difference = result.getIncompatibleDifferences().iterator().next();
        return difference.asRuleViolation();
    }
}
