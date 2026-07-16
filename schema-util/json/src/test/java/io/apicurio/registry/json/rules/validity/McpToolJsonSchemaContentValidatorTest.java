package io.apicurio.registry.json.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.rules.violation.RuleViolation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests helper behavior in the JSON-side MCP tool validator.
 */
public class McpToolJsonSchemaContentValidatorTest {

    @Test
    public void testValidateEmbeddedSchemaWithNullNodeAddsViolation() throws Exception {
        McpToolJsonSchemaContentValidator validator = new McpToolJsonSchemaContentValidator();
        Set<RuleViolation> violations = new HashSet<>();

        invokeValidateEmbeddedSchema(validator, "inputSchema", null, violations);

        Assertions.assertEquals(1, violations.size());
        RuleViolation violation = violations.iterator().next();
        Assertions.assertEquals("'inputSchema' field must be an object",
                violation.getDescription());
        Assertions.assertEquals("/inputSchema", violation.getContext());
    }

    @Test
    public void testPrefixSchemaLocationStripsHashPrefix() throws Exception {
        McpToolJsonSchemaContentValidator validator = new McpToolJsonSchemaContentValidator();

        String path = invokePrefixSchemaLocation(validator, "inputSchema",
                "#/properties/query/type");

        Assertions.assertEquals("/inputSchema/properties/query/type", path);
    }

    @Test
    public void testPrefixSchemaLocationHandlesLeadingSlash() throws Exception {
        McpToolJsonSchemaContentValidator validator = new McpToolJsonSchemaContentValidator();

        String path = invokePrefixSchemaLocation(validator, "outputSchema",
                "/properties/total/type");

        Assertions.assertEquals("/outputSchema/properties/total/type", path);
    }

    @Test
    public void testPrefixSchemaLocationHandlesRootPointer() throws Exception {
        McpToolJsonSchemaContentValidator validator = new McpToolJsonSchemaContentValidator();

        String path = invokePrefixSchemaLocation(validator, "inputSchema", "#");

        Assertions.assertEquals("/inputSchema", path);
    }

    private void invokeValidateEmbeddedSchema(McpToolJsonSchemaContentValidator validator,
            String fieldName, JsonNode schemaNode, Set<RuleViolation> violations)
            throws Exception {
        Method method = McpToolJsonSchemaContentValidator.class.getDeclaredMethod(
                "validateEmbeddedSchema", String.class, JsonNode.class, java.util.Map.class,
                Set.class);
        method.setAccessible(true);
        method.invoke(validator, fieldName, schemaNode, Collections.emptyMap(), violations);
    }

    private String invokePrefixSchemaLocation(McpToolJsonSchemaContentValidator validator,
            String fieldName, String schemaLocation) throws Exception {
        Method method = McpToolJsonSchemaContentValidator.class.getDeclaredMethod(
                "prefixSchemaLocation", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(validator, fieldName, schemaLocation);
    }
}
