package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.DiffContext;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.DiffUtil;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonSchemaDiffUtilTest {
    public static Stream<Arguments> multipleOfCases() {
        return Stream.of(Arguments.of(10, 5, false), Arguments.of(10.0, 5, false),
                Arguments.of(10.0, 5.0, false), Arguments.of(10.0, 10, false),
                Arguments.of(10.0, 10.0, false), Arguments.of(10.1, 10, true), Arguments.of(13, 5, true),
                Arguments.of(13.0, 5, true), Arguments.of(13, 5.0, true));
    }

    @ParameterizedTest
    @MethodSource("multipleOfCases")
    public void multipleOfDivisibility(Number original, Number updated, boolean isIncompatible) {
        DiffContext context = DiffContext.createRootContext();
        DiffUtil.diffNumberOriginalMultipleOfUpdated(context, original, updated,
                DiffType.NUMBER_TYPE_MULTIPLE_OF_UPDATED_IS_DIVISIBLE,
                DiffType.NUMBER_TYPE_MULTIPLE_OF_UPDATED_IS_NOT_DIVISIBLE);
        assertEquals(context.foundIncompatibleDifference(), isIncompatible);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://json-schema.org/draft/2019-09/schema",
            "https://json-schema.org/draft/2020-12/schema" })
    public void unsupportedDraftIsUnprocessable(String metaSchema) {
        String schema = "{\"$schema\":\"" + metaSchema + "\",\"type\":\"object\"}";
        UnprocessableSchemaException ex = assertThrows(UnprocessableSchemaException.class,
                () -> JsonSchemaDiffLibrary.findDifferences(schema, schema, Map.of()));
        assertTrue(ex.getMessage().startsWith("Schema could not be processed for compatibility check: "),
                ex.getMessage());
    }
}
