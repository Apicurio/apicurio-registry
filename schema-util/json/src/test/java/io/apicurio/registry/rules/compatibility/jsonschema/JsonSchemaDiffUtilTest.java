package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffContext;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
