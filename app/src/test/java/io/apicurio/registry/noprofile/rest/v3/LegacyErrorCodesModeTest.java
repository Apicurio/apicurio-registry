package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ConflictOrRuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

@QuarkusTest
@TestProfile(LegacyErrorCodesModeTest.LegacyErrorCodesProfile.class)
public class LegacyErrorCodesModeTest extends AbstractResourceTestBase {

    public static class LegacyErrorCodesProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Collections.singletonMap("apicurio.rest.legacy-error-codes.enabled", "true");
        }
    }

    private static final String INVALID_OPENAPI = """
            {
                "openapi": "3.0.2",
                "info": {
                    "title": "Test API"
                }
            }
            """;

    @Test
    public void testLegacy409RuleViolationExposesCausesOnDraftFinalize() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(
                artifactId, ArtifactType.OPENAPI, INVALID_OPENAPI, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setIsDraft(true);
        createArtifact.getFirstVersion().setVersion("1.0.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).rules().post(createRule);

        WrappedVersionState newState = new WrappedVersionState();
        newState.setState(VersionState.ENABLED);

        ConflictOrRuleViolationProblemDetails error = Assertions.assertThrows(
                ConflictOrRuleViolationProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId)
                        .artifacts().byArtifactId(artifactId)
                        .versions().byVersionExpression("1.0.0")
                        .state().put(newState)
        );

        Assertions.assertEquals(409, error.getStatus());
        Assertions.assertNotNull(error.getCauses());
        Assertions.assertFalse(error.getCauses().isEmpty());
    }

    @Test
    public void testLegacy409RuleViolationExposesCausesOnCreateArtifact() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.admin().rules().post(createRule);

        try {
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(
                    artifactId, ArtifactType.OPENAPI, INVALID_OPENAPI, ContentTypes.APPLICATION_JSON);

            ConflictOrRuleViolationProblemDetails error = Assertions.assertThrows(
                    ConflictOrRuleViolationProblemDetails.class,
                    () -> clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact)
            );

            Assertions.assertEquals(409, error.getStatus());
            Assertions.assertNotNull(error.getCauses());
            Assertions.assertFalse(error.getCauses().isEmpty());
        } finally {
            clientV3.admin().rules().byRuleType(RuleType.VALIDITY.name()).delete();
        }
    }
}
