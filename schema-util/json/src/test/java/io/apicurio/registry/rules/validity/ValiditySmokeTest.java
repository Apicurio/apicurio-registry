package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.rules.validity.ValiditySmokeTest.TestCases.TestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.MAPPER;
import static io.apicurio.registry.rules.compatibility.CompatibilityTestExecutor.readResource;
import static io.apicurio.registry.rules.compatibility.jsonschema.JsonUtil.readSchema;

public class ValiditySmokeTest {


    public static Stream<TestCase> cases() throws JsonProcessingException {
        var rawData = readResource(ValiditySmokeTest.class, "test-data.json");
        var testCases = MAPPER.readValue(rawData, TestCases.class);
        return testCases.tests.stream();
    }

    @ParameterizedTest
    @MethodSource("cases")
    void testValidity(TestCase testCase) {
        var versions = Map.of(
                "draft-04", "http://json-schema.org/draft-04/schema",
                "draft-06", "http://json-schema.org/draft-06/schema",
                "draft-07", "http://json-schema.org/draft-07/schema",
                "draft-2020", "https://json-schema.org/draft/2020-12/schema"
        );
        for (Entry<String, String> vesionEntry : versions.entrySet()) {
            var jsonNode = testCase.schema.deepCopy();
            ObjectNode objectNode = (ObjectNode) jsonNode;
            objectNode.put("$schema", vesionEntry.getValue());

            try {
                readSchema(objectNode.toString(), Map.of(), false);
                if (!testCase.expected.get(vesionEntry.getKey())) {
                    Assertions.fail("Test case '%s' failed on version %s, expected false but was true."
                            .formatted(testCase.description, vesionEntry.getKey()));
                }
            } catch (Exception ex) {
                if (testCase.expected.get(vesionEntry.getKey())) {
                    Assertions.fail("Test case '%s' failed on version %s, expected true but was false: %s"
                            .formatted(testCase.description, vesionEntry.getKey(), ex.getMessage()));
                }
            }
        }
    }

    public static class TestCases {

        public List<TestCase> tests;

        public static class TestCase {

            public String description;
            public JsonNode schema;
            public Map<String, Boolean> expected;
        }
    }
}
