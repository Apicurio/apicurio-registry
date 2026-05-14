package io.apicurio.registry.rules.validity;

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
        Assertions.assertFalse(error.getCauses().isEmpty());
        // Should have violations for title (not string), audience (not array),
        // and priority (not number)
        Assertions.assertTrue(error.getCauses().size() >= 3);
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
