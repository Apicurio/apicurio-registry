package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.content.McpToolContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.content.extract.McpToolContentExtractor;
import io.apicurio.registry.rules.violation.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Tests the MCP tool content validator, accepter, and extractor.
 */
public class McpToolContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidMcpTool() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-valid.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testValidMcpToolSyntaxOnly() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-valid.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testMcpToolMissingName() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-missing-name.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
        Assertions.assertFalse(error.getCauses().isEmpty());
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("name")));
    }

    @Test
    public void testMcpToolMissingInputSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-missing-inputschema.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
        Assertions.assertFalse(error.getCauses().isEmpty());
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("inputSchema")));
    }

    @Test
    public void testMcpToolInvalidJson() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-invalid-json.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testMcpToolInvalidInputSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-invalid-inputschema.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
        Assertions.assertFalse(error.getCauses().isEmpty());
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("inputSchema")));
    }

    @Test
    public void testMcpToolInvalidAnnotations() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-invalid-annotations.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
        // Violations for title (not a string), readOnlyHint and destructiveHint (not booleans)
        Assertions.assertEquals(3, error.getCauses().size());
        Assertions.assertTrue(error.getCauses().stream()
                .anyMatch(v -> "'title' field must be a string".equals(v.getDescription())));
        Assertions.assertTrue(error.getCauses().stream()
                .anyMatch(v -> "'annotations.readOnlyHint' must be a boolean"
                        .equals(v.getDescription())));
        Assertions.assertTrue(error.getCauses().stream()
                .anyMatch(v -> "'annotations.destructiveHint' must be a boolean"
                        .equals(v.getDescription())));
    }

    @Test
    public void testMcpToolAnnotationsRejectUnsupportedFields() throws Exception {
        // audience and priority belong to the MCP content annotation schema, not to
        // ToolAnnotations, so at FULL they are rejected as unsupported rather than range-checked.
        TypedContent content = resourceToTypedContentHandle(
                "mcptool-annotations-content-fields.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
        Assertions.assertEquals(2, error.getCauses().size());
        Assertions.assertTrue(error.getCauses().stream()
                .anyMatch(v -> "'annotations.audience' is not a ToolAnnotations property"
                        .equals(v.getDescription())));
        Assertions.assertTrue(error.getCauses().stream()
                .anyMatch(v -> "'annotations.priority' is not a ToolAnnotations property"
                        .equals(v.getDescription())));
    }

    @Test
    public void testMcpToolMinimal() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-minimal.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testMcpToolAccepterAccepts() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-valid.json");
        McpToolContentAccepter accepter = new McpToolContentAccepter();
        Assertions.assertTrue(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testMcpToolAccepterRejectsAgentCard() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-valid.json");
        McpToolContentAccepter accepter = new McpToolContentAccepter();
        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testMcpToolAccepterRejectsInvalidJson() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-invalid-json.json");
        McpToolContentAccepter accepter = new McpToolContentAccepter();
        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testMcpToolAccepterRejectsMalformedRequiredFields() {
        McpToolContentAccepter accepter = new McpToolContentAccepter();

        Assertions.assertFalse(accepter.acceptsContent(createMcpTool("{\"name\":null,\"inputSchema\":null}"),
                Collections.emptyMap()));
        Assertions.assertFalse(accepter.acceptsContent(createMcpTool("{\"name\":1,\"inputSchema\":{}}"),
                Collections.emptyMap()));
        Assertions.assertFalse(accepter.acceptsContent(createMcpTool("{\"name\":\"  \",\"inputSchema\":{}}"),
                Collections.emptyMap()));
        Assertions.assertFalse(accepter.acceptsContent(createMcpTool("{\"name\":\"tool\",\"inputSchema\":[]}"),
                Collections.emptyMap()));
    }

    private TypedContent createMcpTool(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    @Test
    public void testMcpToolExtractor() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-valid.json");
        McpToolContentExtractor extractor = new McpToolContentExtractor();
        ExtractedMetaData metaData = extractor.extract(content.getContent());
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals("search_database", metaData.getName());
        Assertions.assertEquals("Search the product database with filters",
                metaData.getDescription());
    }

    @Test
    public void testMcpToolValidationNoneLevel() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcptool-invalid-json.json");
        McpToolContentValidator validator = new McpToolContentValidator();
        validator.validate(ValidityLevel.NONE, content, Collections.emptyMap());
    }
}
