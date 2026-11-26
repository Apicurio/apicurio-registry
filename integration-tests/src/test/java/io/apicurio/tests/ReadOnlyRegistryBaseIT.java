package io.apicurio.tests;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.deployment.RegistryDeploymentManager;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReadOnlyRegistryBaseIT extends ApicurioRegistryBaseIT {

    protected static final String GROUP_ID = "foo";
    protected static final String ARTIFACT_ID = "petstore";

    private static final String VALID_OPENAPI = """
            {"openapi":"3.0.2","info":{"title":"Test","version":"1.0.0"},"paths":{}}""";

    @Override
    @BeforeAll
    void prepareRestAssured() throws Exception {
        RegistryDeploymentManager.verifyDeploymentSucceeded();
        vertx = Vertx.vertx();
        registryClient = createRegistryClient(vertx);
        RestAssured.baseURI = getRegistryV3ApiUrl();
        logger.info("RestAssured configured with {}", RestAssured.baseURI);
        RestAssured.defaultParser = Parser.JSON;
        RestAssured.urlEncodingEnabled = false;
    }

    @Test
    public void testGroupIsAccessible() throws Exception {
        retry(() -> {
            GroupMetaData group = registryClient.groups().byGroupId(GROUP_ID).get();
            assertNotNull(group);
            assertEquals(GROUP_ID, group.getGroupId());
        });
    }

    @Test
    public void testArtifactIsAccessible() throws Exception {
        retry(() -> {
            ArtifactMetaData artifact = registryClient.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID).get();
            assertNotNull(artifact);
            assertEquals(ARTIFACT_ID, artifact.getArtifactId());
            assertEquals(GROUP_ID, artifact.getGroupId());
        });
    }

    @Test
    public void testArtifactSearchWorks() throws Exception {
        retry(() -> {
            ArtifactSearchResults results = registryClient.groups().byGroupId(GROUP_ID)
                    .artifacts().get();
            assertNotNull(results);
            Assertions.assertTrue(results.getCount() > 0,
                    "Expected at least one artifact in group " + GROUP_ID);

            final boolean found = results.getArtifacts().stream()
                    .anyMatch(a -> ARTIFACT_ID.equals(a.getArtifactId()));
            Assertions.assertTrue(found, "Expected to find artifact " + ARTIFACT_ID);
        });
    }

    @Test
    public void testArtifactVersionIsAccessible() throws Exception {
        retry(() -> {
            VersionMetaData version = registryClient.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .versions().byVersionExpression("1").get();
            assertNotNull(version);
            assertEquals("1", version.getVersion());
            assertEquals(ARTIFACT_ID, version.getArtifactId());
            assertEquals(GROUP_ID, version.getGroupId());
        });
    }

    @Test
    public void testArtifactContentIsAccessible() throws Exception {
        retry(() -> {
            try (InputStream content = registryClient.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .versions().byVersionExpression("1").content().get()) {
                assertNotNull(content, "Artifact content should not be null");
                final String body = new String(content.readAllBytes(), StandardCharsets.UTF_8);
                Assertions.assertTrue(body.contains("Petstore"),
                        "Expected content to contain 'Petstore' but got: " + body);
            }
        });
    }

    @Test
    public void testArtifactRuleExists() throws Exception {
        retry(() -> {
            Rule rule = registryClient.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .rules().byRuleType(RuleType.COMPATIBILITY.getValue()).get();
            assertNotNull(rule);
            assertEquals(RuleType.COMPATIBILITY, rule.getRuleType());
            assertEquals("BACKWARD", rule.getConfig());
        });
    }

    @Test
    public void testGlobalRuleExists() throws Exception {
        retry(() -> {
            List<RuleType> rules = registryClient.admin().rules().get();
            assertNotNull(rules);
            Assertions.assertTrue(rules.contains(RuleType.VALIDITY),
                    "Expected VALIDITY global rule to exist");
        });
    }

    @Test
    public void testGlobalRuleConfig() throws Exception {
        retry(() -> {
            Rule rule = registryClient.admin().rules()
                    .byRuleType(RuleType.VALIDITY.getValue()).get();
            assertNotNull(rule);
            assertEquals(RuleType.VALIDITY, rule.getRuleType());
            assertEquals("FULL", rule.getConfig());
        });
    }

    @Test
    public void testWriteOperationIsRejected() throws Exception {
        assertClientError("ReadOnlyStorageException", 409, () -> {
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(
                    "write-test-artifact", ArtifactType.OPENAPI,
                    VALID_OPENAPI, ContentTypes.APPLICATION_JSON);
            registryClient.groups().byGroupId("default").artifacts().post(createArtifact);
        }, errorCodeExtractor);
    }
}
