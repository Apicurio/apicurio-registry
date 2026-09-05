package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.McpServerContentAccepter;
import io.apicurio.registry.content.McpToolContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.content.extract.McpServerContentExtractor;
import io.apicurio.registry.content.extract.McpServerStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests the MCP server definition content validator, accepter, and extractors.
 */
public class McpServerContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidMcpServer() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcpserver-valid.json");
        new McpServerContentValidator().validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testSyntaxOnlyIgnoresSemanticProblems() throws Exception {
        // Missing 'version' is a FULL-level violation, but the document is well-formed JSON.
        TypedContent content = resourceToTypedContentHandle("mcpserver-missing-version.json");
        new McpServerContentValidator().validate(ValidityLevel.SYNTAX_ONLY, content,
                Collections.emptyMap());
    }

    @Test
    public void testMissingVersion() throws Exception {
        assertViolationMentions("mcpserver-missing-version.json", "version");
    }

    @Test
    public void testNameWithoutNamespaceIsRejected() throws Exception {
        assertViolationMentions("mcpserver-bad-name.json", "reverse-DNS");
    }

    @Test
    public void testTraversalNameIsRejected() throws Exception {
        assertViolationMentions("mcpserver-traversal-name.json", "reverse-DNS");
    }

    @Test
    public void testUnknownTransportIsRejected() throws Exception {
        assertViolationMentions("mcpserver-bad-transport.json", "transport");
    }

    @Test
    public void testBareStringTransportIsValid() throws Exception {
        // 'transport' may be a bare string or an object with a 'type' - both forms appear in the wild.
        // mcpserver-valid.json only exercises the object form, so this covers the string form separately.
        TypedContent content = resourceToTypedContentHandle("mcpserver-bare-transport.json");
        new McpServerContentValidator().validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testUnknownRemoteTypeIsRejected() throws Exception {
        // validateRemotesField has its own type-enum check, independent of validateTransport for packages.
        assertViolationMentions("mcpserver-bad-remote-type.json", "type");
    }

    @Test
    public void testVersionWrongTypeIsRejected() throws Exception {
        assertViolationMentions("mcpserver-bad-version-type.json", "version");
    }

    @Test
    public void testMalformedJsonIsRejected() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcpserver-invalid-json.json");
        McpServerContentValidator validator = new McpServerContentValidator();
        Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.FULL, content, Collections.emptyMap()));
    }

    @Test
    public void testRepositoryWithoutUrlIsRejected() throws Exception {
        assertViolationMentions("mcpserver-bad-repository.json", "url");
    }

    @Test
    public void testRepositorySourceWrongTypeReportsANestedPointer() throws Exception {
        // The violation's JSON pointer is a published field that reaches API clients - it must point at
        // '/repository/source', not '/source', since 'source' only exists nested under 'repository'.
        TypedContent content = resourceToTypedContentHandle("mcpserver-bad-repository-source-type.json");
        McpServerContentValidator validator = new McpServerContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.FULL, content, Collections.emptyMap()));
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> "/repository/source".equals(v.getContext())),
                "Expected a violation at '/repository/source', got: " + error.getCauses());
    }

    @Test
    public void testReferencesAreNotSupported() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcpserver-valid.json");
        McpServerContentValidator validator = new McpServerContentValidator();

        ArtifactReference reference = new ArtifactReference();
        reference.setName("ref");
        Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validateReferences(content, List.of(reference)));
    }

    @Test
    public void testNoReferencesIsAccepted() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcpserver-valid.json");
        McpServerContentValidator validator = new McpServerContentValidator();
        Assertions.assertDoesNotThrow(() -> validator.validateReferences(content, Collections.emptyList()));
        Assertions.assertDoesNotThrow(() -> validator.validateReferences(content, null));
    }

    // === Accepter ===

    @Test
    public void testAccepterAcceptsAServerDefinition() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcpserver-valid.json");
        Assertions.assertTrue(
                new McpServerContentAccepter().acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testAccepterRejectsAToolDefinition() throws Exception {
        // A tool definition also has a 'name', so the two types must not be confused for each other.
        TypedContent content = resourceToTypedContentHandle("mcptool-valid.json");
        Assertions.assertFalse(
                new McpServerContentAccepter().acceptsContent(content, Collections.emptyMap()));
        Assertions
                .assertTrue(new McpToolContentAccepter().acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testToolAccepterRejectsAServerDefinition() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcpserver-valid.json");
        Assertions.assertFalse(
                new McpToolContentAccepter().acceptsContent(content, Collections.emptyMap()));
    }

    // === Extractors ===

    @Test
    public void testExtractsNameAndDescription() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcpserver-valid.json");
        ExtractedMetaData metaData = new McpServerContentExtractor().extract(content.getContent());
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals("io.github.example/weather", metaData.getName());
        Assertions.assertEquals("A weather MCP server", metaData.getDescription());
    }

    @Test
    public void testExtractsPackagesAndTransportsForIndexing() throws Exception {
        TypedContent content = resourceToTypedContentHandle("mcpserver-valid.json");
        List<StructuredElement> elements = new McpServerStructuredContentExtractor()
                .extract(content.getContent());

        Assertions.assertTrue(containsElement(elements, "packageRegistry", "npm"));
        Assertions.assertTrue(containsElement(elements, "package", "@example/weather-mcp"));
        Assertions.assertTrue(containsElement(elements, "transport", "stdio"));
        Assertions.assertTrue(containsElement(elements, "transport", "streamable-http"));
        Assertions.assertTrue(
                containsElement(elements, "remote", "https://weather.example.com/mcp"));
    }

    private boolean containsElement(List<StructuredElement> elements, String kind, String name) {
        return elements.stream().anyMatch(e -> kind.equals(e.kind()) && name.equals(e.name()));
    }

    private void assertViolationMentions(String resource, String expected) throws Exception {
        TypedContent content = resourceToTypedContentHandle(resource);
        McpServerContentValidator validator = new McpServerContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class,
                () -> validator.validate(ValidityLevel.FULL, content, Collections.emptyMap()));
        Assertions.assertFalse(error.getCauses().isEmpty());
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains(expected)),
                "Expected a violation mentioning '" + expected + "', got: " + error.getCauses());
    }
}
