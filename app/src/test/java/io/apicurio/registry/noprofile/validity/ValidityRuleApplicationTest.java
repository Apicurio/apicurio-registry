package io.apicurio.registry.noprofile.validity;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class ValidityRuleApplicationTest extends AbstractResourceTestBase {

    private static final String SCHEMA_SIMPLE = "{\"type\": \"string\"}";
    private static final String INVALID_SCHEMA = "{\"type\": \"string";

    private static final String SCHEMA_WITH_MAP = """
            {
                "type": "record",
                "name": "userInfo",
                "namespace": "my.example",
                "fields": [
                    {
                        "name": "name",
                        "type": "string",
                        "default": "NONE"
                    },
                    {
                        "name": "props",
                        "type": {
                            "type": "map",
                            "values": "string"
                        }
                    }
                ]
            }""";
    private static final String INVALID_SCHEMA_WITH_MAP = """
            {
                "type": "record",
                "name": "userInfo",
                "namespace": "my.example",
                "fields": [
                    {
                        "name": "name",
                        "type": "string",
                        "default": "NONE"
                    },
                    {
                        "name": "props",
                        "type": {
                            "type": "map",
                            "values": "string"
                        },
                        "default": "{}"
                    }
                ]
            }""";

    @Test
    public void testValidityRuleApplication() throws Exception {
        String artifactId = "ValidityRuleApplicationTest";
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                .byArtifactId(artifactId).rules().post(createRule);

        var exception = Assertions.assertThrows(
                io.apicurio.registry.rest.client.models.RuleViolationProblemDetails.class, () -> {
                    createArtifactVersion(artifactId, INVALID_SCHEMA, ContentTypes.APPLICATION_JSON);
                });
        assertEquals("RuleViolationException", exception.getName());
        assertEquals(400, exception.getStatus());
    }

    @Test
    public void testValidityRuleApplication_Map() throws Exception {
        String artifactId = "testValidityRuleApplication_Map";
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_WITH_MAP, ContentTypes.APPLICATION_JSON);
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                .byArtifactId(artifactId).rules().post(createRule);

        var exception = Assertions.assertThrows(
                io.apicurio.registry.rest.client.models.RuleViolationProblemDetails.class, () -> {
                    createArtifactVersion(artifactId, INVALID_SCHEMA_WITH_MAP, ContentTypes.APPLICATION_JSON);
                });
        assertEquals("RuleViolationException", exception.getName());
        assertEquals(400, exception.getStatus());
    }

    @Test
    public void testValidityRuleGroupConfig() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testValidityRuleGroupConfig";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Create a group level rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        // Try to create an invalid artifact in that group
        var exception = Assertions.assertThrows(
                io.apicurio.registry.rest.client.models.RuleViolationProblemDetails.class, () -> {
                    createArtifact(groupId, artifactId, ArtifactType.AVRO, INVALID_SCHEMA,
                            ContentTypes.APPLICATION_JSON);
                });
        assertEquals("RuleViolationException", exception.getName());
        assertEquals(400, exception.getStatus());
    }

    @Test
    public void testValidityRuleGlobalConfig() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "testValidityRuleGlobalConfig";

        // Create a global rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.admin().rules().post(createRule);

        // Try to create an invalid artifact
        var exception = Assertions.assertThrows(
                io.apicurio.registry.rest.client.models.RuleViolationProblemDetails.class, () -> {
                    createArtifact(groupId, artifactId, ArtifactType.AVRO, INVALID_SCHEMA,
                            ContentTypes.APPLICATION_JSON);
                });
        assertEquals("RuleViolationException", exception.getName());
        assertEquals(400, exception.getStatus());
    }

    @Test
    public void testDraftFinalizeRuleViolationExposesCauses() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Valid JSON but semantically invalid OpenAPI (missing version and paths)
        String invalidOpenApi = """
                {
                    "openapi": "3.0.2",
                    "info": {
                        "title": "Test API"
                    }
                }
                """;

        // 1. Create artifact with an invalid DRAFT version (bypasses rules at creation)
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(
                artifactId, ArtifactType.OPENAPI, invalidOpenApi, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // 2. Enable a VALIDITY rule with FULL level — now finalization will be checked
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).rules().post(createRule);

        // 3. Attempt finalize (DRAFT -> ENABLED) — must fail with causes
        WrappedVersionState newState = new WrappedVersionState();
        newState.setState(VersionState.ENABLED);

        ProblemDetails error = Assertions.assertThrows(
                ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId)
                        .artifacts().byArtifactId(artifactId)
                        .versions().byVersionExpression("1.0.0")
                        .state().put(newState)
        );

        // 4. THE KEY ASSERTION — ruleViolation and causes must be present and non-empty
        Assertions.assertNotNull(error.getRuleViolation(),
                "ruleViolation must not be null on draft finalize rule violation");
        Assertions.assertNotNull(error.getRuleViolation().getCauses(),
                "Causes must not be null on draft finalize rule violation");
        Assertions.assertFalse(error.getRuleViolation().getCauses().isEmpty(),
                "Causes must be populated on draft finalize rule violation");
        Assertions.assertTrue(
                error.getRuleViolation().getCauses().stream().anyMatch(c -> c.getContext() != null && c.getContext().contains("paths")),
                "Expected causes to include context referring to /paths"
        );
        Assertions.assertEquals(400, error.getStatus());
    }

    @Test
    public void testCreateArtifactRuleViolationExposesBothTopLevelAndNestedCauses() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).rules().post(createRule);

        String invalidOpenApi = """
                {
                    "openapi": "3.0.2",
                    "info": {
                        "title": "Test API"
                    }
                }
                """;

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(
                artifactId, ArtifactType.OPENAPI, invalidOpenApi, ContentTypes.APPLICATION_JSON);

        var error = Assertions.assertThrows(
                io.apicurio.registry.rest.client.models.RuleViolationProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
        );

        Assertions.assertEquals(400, error.getStatus());
        // Verify deprecated top-level causes is populated
        Assertions.assertNotNull(error.getCauses());
        Assertions.assertFalse(error.getCauses().isEmpty());
        // Verify new ruleViolation.causes is populated
        Assertions.assertNotNull(error.getRuleViolation());
        Assertions.assertNotNull(error.getRuleViolation().getCauses());
        // Verify backward-compatibility: both cause lists are equal
        Assertions.assertEquals(error.getCauses().size(), error.getRuleViolation().getCauses().size());
        for (int i = 0; i < error.getCauses().size(); i++) {
            Assertions.assertEquals(error.getCauses().get(i).getContext(), error.getRuleViolation().getCauses().get(i).getContext());
            Assertions.assertEquals(error.getCauses().get(i).getDescription(), error.getRuleViolation().getCauses().get(i).getDescription());
        }
        // Assert specific cause context
        Assertions.assertTrue(
                error.getRuleViolation().getCauses().stream().anyMatch(c -> c.getContext() != null && c.getContext().contains("paths")),
                "Expected causes to include context referring to /paths"
        );
    }

}
