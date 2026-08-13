package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for AgentCardCompatibilityChecker (v1.0 format).
 */
class AgentCardCompatibilityCheckerTest {

    private AgentCardCompatibilityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new AgentCardCompatibilityChecker();
    }

    private TypedContent createAgentCard(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    private static String baseCard(String skills, String extras) {
        return """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [%s],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]%s
                }
                """.formatted(skills, extras);
    }

    private static final String SKILL1 =
            """
            { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }""";
    private static final String SKILL2 =
            """
            { "id": "skill2", "name": "Skill 2", "description": "Another skill", "tags": ["test"] }""";

    @Test
    void testCompatibleWhenNoExistingArtifacts() {
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                Collections.emptyList(),
                createAgentCard(baseCard(SKILL1, "")),
                Map.of());

        assertTrue(result.isCompatible(), "Should be compatible when no existing artifacts");
    }

    @Test
    void testBackwardCompatibleAddingSkill() {
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1 + "," + SKILL2, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Adding a skill should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleRemovingSkill() {
        String existing = baseCard(SKILL1 + "," + SKILL2, "");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Removing a skill should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("skill2")));
    }

    @Test
    void testBackwardIncompatibleInterfaceRemoval() {
        String existing = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" },
                        { "url": "https://example.com/agent", "protocolBinding": "jsonrpc", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [%s],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """.formatted(SKILL1);

        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Removing an interface should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("Interface")
                        && d.asRuleViolation().getDescription().contains("removed")));
    }

    @Test
    void testBackwardCompatibleAddingCapability() {
        String existing = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}", "\"capabilities\": { \"streaming\": false }");
        String proposed = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": true, \"pushNotifications\": true }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "Adding or enabling capabilities should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleDisablingCapability() {
        String existing = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": true, \"pushNotifications\": true }");
        String proposed = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": true, \"pushNotifications\": false }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Disabling a capability should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("pushNotifications")));
    }

    @Test
    void testBackwardIncompatibleRemovingSecurityScheme() {
        String twoSchemes = """
                ,
                    "securitySchemes": {
                        "bearer": { "type": "httpAuth", "scheme": "Bearer" },
                        "apikey": { "type": "apiKey", "name": "X-API-Key", "location": "header" }
                    }""";
        String oneScheme = """
                ,
                    "securitySchemes": {
                        "bearer": { "type": "httpAuth", "scheme": "Bearer" }
                    }""";

        String existing = baseCard(SKILL1, twoSchemes);
        String proposed = baseCard(SKILL1, oneScheme);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing security scheme should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("apikey")));
    }

    @Test
    void testBackwardCompatibleAddingInputMode() {
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1, "").replace(
                "\"defaultInputModes\": [\"text\"]",
                "\"defaultInputModes\": [\"text\", \"image\"]");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Adding input modes should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleRemovingInputMode() {
        String existing = baseCard(SKILL1, "").replace(
                "\"defaultInputModes\": [\"text\"]",
                "\"defaultInputModes\": [\"text\", \"image\"]");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing input modes should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("image")));
    }

    @Test
    void testBackwardIncompatibleRemovingOutputMode() {
        String existing = baseCard(SKILL1, "").replace(
                "\"defaultOutputModes\": [\"text\"]",
                "\"defaultOutputModes\": [\"text\", \"json\"]");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing output modes should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("json")));
    }

    @Test
    void testForwardCompatibleRemovingSkill() {
        String existing = baseCard(SKILL1 + "," + SKILL2, "");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.FORWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Removing a skill should be forward compatible");
    }

    @Test
    void testFullCompatibleNameChange() {
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1, "").replace("\"name\": \"TestAgent\"",
                "\"name\": \"NewName\"");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.FULL,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Changing name should be fully compatible");
    }

    @Test
    void testMultipleIncompatibilities() {
        String existing = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" },
                        { "url": "https://example.com/agent", "protocolBinding": "jsonrpc", "protocolVersion": "1.0" }
                    ],
                    "capabilities": { "streaming": true },
                    "skills": [%s, %s],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """.formatted(SKILL1, SKILL2);

        String proposed = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}", "\"capabilities\": { \"streaming\": false }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible());
        assertEquals(3, result.getIncompatibleDifferences().size(),
                "Should report interface removal, skill removal, and capability removal");
    }

    @Test
    void testBackwardIncompatibleProtocolVersionChange() {
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1, "").replace(
                "\"protocolVersion\": \"1.0\"",
                "\"protocolVersion\": \"2.0\"");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Changing protocol version should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription()
                        .contains("Protocol version changed")));
    }

    private static final String TRANSLATE_EXTENSION =
            """
            { "uri": "https://example.com/ext/translate", "description": "Translation" }""";
    private static final String SUMMARISE_EXTENSION =
            """
            { "uri": "https://example.com/ext/summarise", "description": "Summarisation" }""";

    private static String cardWithExtensions(String extensions) {
        return baseCard(SKILL1, "").replace("\"capabilities\": {}",
                "\"capabilities\": { \"extensions\": [" + extensions + "] }");
    }

    @Test
    void testBackwardIncompatibleRemovingCapabilityExtension() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing a declared capability extension should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription()
                        .contains("https://example.com/ext/translate")),
                "The violation should name the removed extension uri");
    }

    @Test
    void testBackwardIncompatibleRemovingOneOfSeveralCapabilityExtensions() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION + "," + SUMMARISE_EXTENSION);
        String proposed = cardWithExtensions(TRANSLATE_EXTENSION);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing one of several capability extensions should be backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "Only the removed extension should be reported");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription()
                        .contains("https://example.com/ext/summarise")));
    }

    @Test
    void testCapabilityExtensionViolationIsReportedAgainstExtensionsPath() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertEquals("/capabilities/extensions",
                result.getIncompatibleDifferences().iterator().next().asRuleViolation().getContext());
    }

    @Test
    void testBackwardCompatibleAddingCapabilityExtension() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = cardWithExtensions(TRANSLATE_EXTENSION + "," + SUMMARISE_EXTENSION);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "Adding a capability extension should be backward compatible");
    }

    @Test
    void testBackwardCompatibleRetainingCapabilityExtension() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = cardWithExtensions(TRANSLATE_EXTENSION).replace(
                "\"description\": \"Translation\"", "\"description\": \"Translation v2\"");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "Keeping an extension uri while editing its description should stay compatible");
    }

    @Test
    void testCapabilityExtensionWithoutUriIsIgnored() {
        String existing = cardWithExtensions("{ \"description\": \"Anonymous extension\" }");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "An extension with no uri cannot be tracked, so it should not be reported");
    }

    @Test
    void testCapabilityExtensionWithNonTextualUriIsIgnored() {
        String existing = cardWithExtensions("{ \"uri\": 42 }");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "A non-textual uri is not an identity, so it should not be reported");
    }

    @Test
    void testCapabilityExtensionWithBlankUriIsIgnored() {
        String existing = cardWithExtensions("{ \"uri\": \"   \" }, { \"uri\": \"\" }");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "A blank uri is not an identity, so it should not be reported as a removal");
    }

    @Test
    void testBackwardIncompatibleRenamingCapabilityExtensionUri() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = cardWithExtensions(SUMMARISE_EXTENSION);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Changing an extension uri drops the old one, so it is backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "The new uri counts as an add, so only the old uri is a removal");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription()
                        .contains("https://example.com/ext/translate")));
    }

    @Test
    void testBooleanCapabilitiesStillCheckedAlongsideExtensions() {
        String existing = baseCard(SKILL1, "").replace("\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": true, \"extensions\": [" + TRANSLATE_EXTENSION
                        + "] }");
        String proposed = baseCard(SKILL1, "").replace("\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": false }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible());
        assertEquals(2, result.getIncompatibleDifferences().size(),
                "Both the disabled boolean capability and the removed extension should be reported");
    }

    private static final String BEARER_SCHEME =
            "{ \"type\": \"httpAuth\", \"scheme\": \"Bearer\" }";
    private static final String APIKEY_HEADER_SCHEME =
            "{ \"type\": \"apiKey\", \"name\": \"X-API-Key\", \"location\": \"header\" }";
    private static final String OAUTH_SCHEME =
            "{ \"type\": \"oauth2\", \"flows\": { \"clientCredentials\": {"
                    + " \"tokenUrl\": \"https://example.com/token\","
                    + " \"scopes\": { \"read\": \"Read access\" } } } }";

    private static String cardWithSchemes(String schemes) {
        return baseCard(SKILL1, ", \"securitySchemes\": " + schemes);
    }

    private static String cardWithScheme(String name, String body) {
        return cardWithSchemes("{ \"" + name + "\": " + body + " }");
    }

    private static boolean reports(CompatibilityExecutionResult result, String text) {
        return result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains(text));
    }

    @Test
    void testBackwardIncompatibleSecuritySchemeTypeChange() {
        String existing = cardWithScheme("bearer", BEARER_SCHEME);
        String proposed = cardWithScheme("bearer", APIKEY_HEADER_SCHEME);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Redefining a retained security scheme should be backward incompatible");
        assertEquals(2, result.getIncompatibleDifferences().size(),
                "Both the changed type and the dropped scheme should be reported");
        assertTrue(reports(result,
                "Security scheme 'bearer' field 'type' changed from 'httpAuth' to 'apiKey'"));
        assertTrue(reports(result,
                "Security scheme 'bearer' field 'scheme' was removed (was 'Bearer')"));
    }

    @Test
    void testSecuritySchemeViolationIsReportedAgainstSecuritySchemesPath() {
        String existing = cardWithScheme("bearer", BEARER_SCHEME);
        String proposed = cardWithScheme("bearer", "{ \"type\": \"mutualTls\" }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible());
        assertEquals(2, result.getIncompatibleDifferences().size(),
                "Both the changed type and the dropped scheme should be reported");
        assertTrue(result.getIncompatibleDifferences().stream()
                .allMatch(d -> "/securitySchemes".equals(d.asRuleViolation().getContext())),
                "Security scheme differences belong to /securitySchemes");
    }

    @Test
    void testBackwardIncompatibleSecuritySchemeLocationChange() {
        String existing = cardWithScheme("apikey", APIKEY_HEADER_SCHEME);
        String proposed = cardWithScheme("apikey",
                "{ \"type\": \"apiKey\", \"name\": \"X-API-Key\", \"location\": \"query\" }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Moving an API key from a header to a query parameter breaks existing clients");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "Only the location field changed");
        assertTrue(reports(result,
                "Security scheme 'apikey' field 'location' changed from 'header' to 'query'"));
    }

    @Test
    void testBackwardIncompatibleRemovingSecuritySchemeField() {
        String existing = cardWithScheme("bearer",
                "{ \"type\": \"httpAuth\", \"scheme\": \"Bearer\", \"bearerFormat\": \"JWT\" }");
        String proposed = cardWithScheme("bearer", BEARER_SCHEME);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Dropping a declared security scheme field should be backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "Only the dropped bearerFormat should be reported");
        assertTrue(reports(result,
                "Security scheme 'bearer' field 'bearerFormat' was removed (was 'JWT')"));
    }

    @Test
    void testBackwardIncompatibleOAuth2FlowTokenUrlChange() {
        String existing = cardWithScheme("oauth", OAUTH_SCHEME);
        String proposed = cardWithScheme("oauth", OAUTH_SCHEME.replace(
                "https://example.com/token", "https://auth.example.com/token"));

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Moving the OAuth2 token endpoint should be backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "Only the changed leaf should be reported, not every field under flows");
        assertTrue(reports(result,
                "Security scheme 'oauth' field 'flows/clientCredentials/tokenUrl' changed from"
                        + " 'https://example.com/token' to 'https://auth.example.com/token'"));
    }

    @Test
    void testBackwardIncompatibleRemovingOAuth2Flow() {
        String existing = cardWithScheme("oauth", OAUTH_SCHEME.replace(
                "\"flows\": {", "\"flows\": { \"authorizationCode\": {"
                        + " \"authorizationUrl\": \"https://example.com/authorize\" },"));
        String proposed = cardWithScheme("oauth", OAUTH_SCHEME);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Withdrawing an OAuth2 grant type should be backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "The whole flow is reported once, not once per field beneath it");
        assertTrue(reports(result,
                "Security scheme 'oauth' no longer declares 'flows/authorizationCode'"));
    }

    @Test
    void testBackwardCompatibleSecuritySchemeDescriptionChange() {
        String existing = cardWithScheme("bearer",
                "{ \"type\": \"httpAuth\", \"scheme\": \"Bearer\","
                        + " \"description\": \"Bearer token\" }");
        String proposed = cardWithScheme("bearer",
                "{ \"type\": \"httpAuth\", \"scheme\": \"Bearer\","
                        + " \"description\": \"Bearer token, JWT encoded\" }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "A security scheme description is documentation, so editing it stays compatible");
    }

    @Test
    void testFullCompatibleSecuritySchemeDescriptionChange() {
        String existing = cardWithScheme("bearer",
                "{ \"type\": \"httpAuth\", \"scheme\": \"Bearer\","
                        + " \"description\": \"Bearer token\" }");
        String proposed = cardWithScheme("bearer",
                "{ \"type\": \"httpAuth\", \"scheme\": \"Bearer\","
                        + " \"description\": \"Bearer token, JWT encoded\" }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.FULL,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "The description exemption must hold in both directions");
    }

    @Test
    void testBackwardCompatibleSecuritySchemeUnknownFieldChange() {
        String existing = cardWithScheme("bearer",
                "{ \"type\": \"httpAuth\", \"scheme\": \"Bearer\", \"x-owner\": \"team-a\" }");
        String proposed = cardWithScheme("bearer",
                "{ \"type\": \"httpAuth\", \"scheme\": \"Bearer\", \"x-owner\": \"team-b\" }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "The schema allows vendor fields, so churn in one must not fail a publish");
    }

    @Test
    void testBackwardCompatibleAddingSecurityScheme() {
        String existing = cardWithScheme("bearer", BEARER_SCHEME);
        String proposed = cardWithSchemes("{ \"bearer\": " + BEARER_SCHEME + ", \"apikey\": "
                + APIKEY_HEADER_SCHEME + " }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "Offering an additional security scheme should be backward compatible");
    }

    @Test
    void testBackwardCompatibleAddingOAuth2FlowAndScope() {
        String existing = cardWithScheme("oauth", OAUTH_SCHEME);
        String proposed = cardWithScheme("oauth", OAUTH_SCHEME
                .replace("\"scopes\": { \"read\": \"Read access\" }",
                        "\"scopes\": { \"read\": \"Read access\", \"write\": \"Write access\" }")
                .replace("\"flows\": {", "\"flows\": { \"authorizationCode\": {"
                        + " \"authorizationUrl\": \"https://example.com/authorize\" },"));

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "Adding an OAuth2 grant type or scope is additive, so it stays compatible");
    }

    @Test
    void testBackwardIncompatibleSecuritySchemeReplacedByNull() {
        String existing = cardWithScheme("bearer", BEARER_SCHEME);
        String proposed = cardWithScheme("bearer", "null");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "The scheme name survives, so this is not caught as a removal, but the definition"
                        + " behind it is gone");
        assertEquals(1, result.getIncompatibleDifferences().size());
        assertTrue(reports(result,
                "Security scheme 'bearer' is no longer defined as an object"));
    }

    @Test
    void testBackwardIncompatibleSecuritySchemeReplacedByScalar() {
        String existing = cardWithScheme("bearer", BEARER_SCHEME);
        String proposed = cardWithScheme("bearer", "\"httpAuth\"");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Collapsing a scheme definition to a scalar should be backward incompatible");
        assertTrue(reports(result,
                "Security scheme 'bearer' is no longer defined as an object"));
    }

    @Test
    void testBackwardIncompatibleSecuritySchemeFieldBecomingNonTextual() {
        String existing = cardWithScheme("bearer", BEARER_SCHEME);
        String proposed = cardWithScheme("bearer",
                "{ \"type\": \"httpAuth\", \"scheme\": 42 }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "The validator never type-checks scheme fields, so a field that turns into a"
                        + " number still has to be compared");
        assertTrue(reports(result,
                "Security scheme 'bearer' field 'scheme' changed from 'Bearer' to '42'"),
                "The value changed, so it must not be described as removed");
    }

    @Test
    void testBackwardCompatibleAddingArrayValuedOAuth2Scope() {
        String existing = cardWithScheme("oauth", "{ \"type\": \"oauth2\", \"flows\": {"
                + " \"clientCredentials\": { \"scopes\": [\"read\"] } } }");
        String proposed = cardWithScheme("oauth", "{ \"type\": \"oauth2\", \"flows\": {"
                + " \"clientCredentials\": { \"scopes\": [\"read\", \"write\"] } } }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "An array of scopes is a set of offered values, so adding one stays compatible");
    }

    @Test
    void testBackwardIncompatibleRemovingArrayValuedOAuth2Scope() {
        String existing = cardWithScheme("oauth", "{ \"type\": \"oauth2\", \"flows\": {"
                + " \"clientCredentials\": { \"scopes\": [\"read\", \"write\"] } } }");
        String proposed = cardWithScheme("oauth", "{ \"type\": \"oauth2\", \"flows\": {"
                + " \"clientCredentials\": { \"scopes\": [\"read\"] } } }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Withdrawing an offered scope should be backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "Only the withdrawn scope should be reported");
        assertTrue(reports(result, "Security scheme 'oauth' no longer offers 'write' in"
                + " 'flows/clientCredentials/scopes'"));
    }

    private static String cardWithoutInterfaceProtocolVersion() {
        return baseCard(SKILL1, "").replace(", \"protocolVersion\": \"1.0\" }", " }");
    }

    @Test
    void testBackwardCompatibleWhenBothInterfacesOmitProtocolVersion() {
        String existing = cardWithoutInterfaceProtocolVersion();
        String proposed = cardWithoutInterfaceProtocolVersion();

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "An interface that never declared a protocolVersion has none to lose, so this must"
                        + " not be reported as a removal");
        assertEquals(0, result.getIncompatibleDifferences().size());
    }

    @Test
    void testBackwardCompatibleWhenProposedInterfaceAddsProtocolVersion() {
        String existing = cardWithoutInterfaceProtocolVersion();
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "Declaring a protocolVersion on an interface that lacked one is additive");
        assertEquals(0, result.getIncompatibleDifferences().size());
    }

    @Test
    void testBackwardIncompatibleCardProtocolVersionChange() {
        String existing = baseCard(SKILL1, ", \"protocolVersion\": \"1.0\"");
        String proposed = baseCard(SKILL1, ", \"protocolVersion\": \"2.0\"");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Changing the card-level protocolVersion should be backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "The interfaces are unchanged, so only the card-level version is reported");
        assertTrue(reports(result, "The agent protocolVersion changed from '1.0' to '2.0'"));
        assertEquals("/protocolVersion",
                result.getIncompatibleDifferences().iterator().next().asRuleViolation()
                        .getContext(),
                "The card-level version is not part of /supportedInterfaces");
    }

    @Test
    void testBackwardCompatibleAddingCardProtocolVersion() {
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1, ", \"protocolVersion\": \"1.0\"");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "The card-level protocolVersion is optional, so declaring one is compatible");
    }

    @Test
    void testBackwardIncompatibleRemovingInterfaceProtocolVersion() {
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1, "").replace(
                ", \"protocolVersion\": \"1.0\" }", " }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Dropping an interface protocolVersion withdraws a guarantee clients relied on");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "The interface itself is retained, so only the lost version is reported");
        assertTrue(reports(result, "Protocol version '1.0' was removed from interface "
                + "https://example.com/agent (http+json)"));
    }
}
