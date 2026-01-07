package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.AgentCardContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.extract.AgentCardContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.rules.violation.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Tests the Agent Card content validator, accepter, and extractor.
 */
public class AgentCardContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidAgentCard() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-valid.json");
        AgentCardContentValidator validator = new AgentCardContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testValidAgentCardSyntaxOnly() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-valid.json");
        AgentCardContentValidator validator = new AgentCardContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testAgentCardMissingName() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-missing-name.json");
        AgentCardContentValidator validator = new AgentCardContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
        Assertions.assertFalse(error.getCauses().isEmpty());
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("name")));
    }

    @Test
    public void testAgentCardInvalidJson() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-invalid-json.json");
        AgentCardContentValidator validator = new AgentCardContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testAgentCardInvalidSkillsType() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-invalid-skills.json");
        AgentCardContentValidator validator = new AgentCardContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
        Assertions.assertFalse(error.getCauses().isEmpty());
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("skills")));
    }

    @Test
    public void testAgentCardAccepterAccepts() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-valid.json");
        AgentCardContentAccepter accepter = new AgentCardContentAccepter();
        Assertions.assertTrue(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testAgentCardAccepterRejectsJsonSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-valid.json");
        AgentCardContentAccepter accepter = new AgentCardContentAccepter();
        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testAgentCardAccepterRejectsInvalidJson() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-invalid-json.json");
        AgentCardContentAccepter accepter = new AgentCardContentAccepter();
        Assertions.assertFalse(accepter.acceptsContent(content, Collections.emptyMap()));
    }

    @Test
    public void testAgentCardExtractor() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-valid.json");
        AgentCardContentExtractor extractor = new AgentCardContentExtractor();
        ExtractedMetaData metaData = extractor.extract(content.getContent());
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals("TestAgent", metaData.getName());
        Assertions.assertEquals("A test AI agent for unit testing", metaData.getDescription());
    }

    @Test
    public void testAgentCardMinimal() throws Exception {
        TypedContent content = resourceToTypedContentHandle("agentcard-minimal.json");
        AgentCardContentValidator validator = new AgentCardContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }
}
