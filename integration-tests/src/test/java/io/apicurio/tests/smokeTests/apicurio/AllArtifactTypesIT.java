package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.apicurio.tests.utils.Constants.SMOKE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(SMOKE)
@QuarkusIntegrationTest
class AllArtifactTypesIT extends ApicurioRegistryBaseIT {

    void doTest(String v1Resource, String v2Resource, String atype, String contentType) throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        // Load/Assert resources exist.
        String v1Content = resourceToString("artifactTypes/" + v1Resource);
        String v2Content = resourceToString("artifactTypes/" + v2Resource);

        // Enable syntax validation global rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("SYNTAX_ONLY");
        registryClient.admin().rules().post(createRule);

        // Make sure we have rule
        retryOp((rc) -> rc.admin().rules().byRuleType(createRule.getRuleType().name()).get());

        // Create artifact
        createArtifact(groupId, artifactId, atype, v1Content, contentType, IfArtifactExists.FAIL, null);

        // Test create new version (valid content)
        CreateVersion testCV = TestUtils.clientCreateVersion(v2Content, contentType);
        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .post(testCV, config -> {
                    config.queryParameters.dryRun = true;
                }));

        // Test create new version (invalid content)
        retryAssertClientError("RuleViolationException", 409, (rc) -> {
            String invalidContent = "{\"This is not a valid content.";
            CreateVersion tcv = TestUtils.clientCreateVersion(invalidContent, contentType);
            rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(tcv,
                    config -> {
                        config.queryParameters.dryRun = true;
                    });
        }, errorCodeExtractor);

        // Update artifact (valid v2 content)
        createArtifactVersion(groupId, artifactId, v2Content, contentType, null);

        // Find artifact by content
        InputStream contentIS = new ByteArrayInputStream(v1Content.getBytes(StandardCharsets.UTF_8));
        VersionSearchResults results = registryClient.search().versions().post(contentIS, contentType,
                config -> {
                    config.queryParameters.groupId = groupId;
                    config.queryParameters.artifactId = artifactId;
                    config.queryParameters.orderby = VersionSortBy.GlobalId;
                    config.queryParameters.order = SortOrder.Desc;
                });
        assertNotNull(results);
        assertTrue(results.getCount() > 0);
        assertNotNull(results.getVersions().get(0).getGlobalId());
        assertEquals(artifactId, results.getVersions().get(0).getArtifactId());
        assertNotNull(results.getVersions().get(0).getVersion());

        // Update artifact (invalid content)
        CreateVersion createVersion = TestUtils.clientCreateVersion("{\"This is not a valid content.",
                contentType);
        TestUtils
                .assertClientError(
                        "RuleViolationException", 409, () -> registryClient.groups().byGroupId(groupId)
                                .artifacts().byArtifactId(artifactId).versions().post(createVersion),
                        errorCodeExtractor);

        // Override Validation rule for the artifact
        createRule.setConfig("NONE");
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .post(createRule);

        // Make sure we have rule
        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .byRuleType(createRule.getRuleType().name()).get());

        // Update artifact (invalid content) - should work now
        VersionMetaData amd2 = createArtifactVersion(groupId, artifactId, "{\"This is not a valid content.",
                ContentTypes.APPLICATION_JSON, null);
        // Make sure artifact is fully registered
        retryOp((rc) -> rc.ids().globalIds().byGlobalId(amd2.getGlobalId()).get());
    }

    @Test
    @Tag(ACCEPTANCE)
    void testAvro() throws Exception {
        doTest("avro/multi-field_v1.json", "avro/multi-field_v2.json", ArtifactType.AVRO,
                ContentTypes.APPLICATION_JSON);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testProtobuf() throws Exception {
        doTest("protobuf/tutorial_v1.proto", "protobuf/tutorial_v2.proto", ArtifactType.PROTOBUF,
                ContentTypes.APPLICATION_PROTOBUF);
    }

    @Test
    @Tag(ACCEPTANCE)
    void testJsonSchema() throws Exception {
        doTest("jsonSchema/person_v1.json", "jsonSchema/person_v2.json", ArtifactType.JSON,
                ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testKafkaConnect() throws Exception {
        doTest("kafkaConnect/simple_v1.json", "kafkaConnect/simple_v2.json", ArtifactType.KCONNECT,
                ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testOpenApi30() throws Exception {
        doTest("openapi/3.0-petstore_v1.json", "openapi/3.0-petstore_v2.json", ArtifactType.OPENAPI,
                ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testAsyncApi() throws Exception {
        doTest("asyncapi/2.0-streetlights_v1.json", "asyncapi/2.0-streetlights_v2.json",
                ArtifactType.ASYNCAPI, ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testGraphQL() throws Exception {
        doTest("graphql/swars_v1.graphql", "graphql/swars_v2.graphql", ArtifactType.GRAPHQL,
                ContentTypes.APPLICATION_GRAPHQL);
    }

    @AfterEach
    void deleteRules() throws Exception {
        registryClient.admin().rules().delete();
        retryOp((rc) -> {
            List<RuleType> rules = rc.admin().rules().get();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}
