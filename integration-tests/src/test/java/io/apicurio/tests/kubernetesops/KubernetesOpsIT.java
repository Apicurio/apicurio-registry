package io.apicurio.tests.kubernetesops;

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
import io.apicurio.tests.ReadOnlyRegistryBaseIT;
import static io.apicurio.deployment.Constants.*;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for KubernetesOps storage.
 * Verifies that registry data loaded from ConfigMaps is accessible via the REST API.
 * <p>
 * The KubernetesOps storage is read-only: all data comes from ConfigMaps, and write
 * operations are not supported. These tests verify read operations only.
 */
@Tag(KUBERNETES_OPS)
@QuarkusIntegrationTest
public class KubernetesOpsIT extends ReadOnlyRegistryBaseIT {

    private static final String GROUP_ID = "foo";
    private static final String ARTIFACT_ID = "petstore";

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

            boolean found = results.getArtifacts().stream()
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
            var content = registryClient.groups().byGroupId(GROUP_ID)
                    .artifacts().byArtifactId(ARTIFACT_ID)
                    .versions().byVersionExpression("1").content().get();
            assertNotNull(content, "Artifact content should not be null");
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
    public void testWriteOperationIsRejected() {
        // KubernetesOps storage is read-only, so create operations should fail
        Assertions.assertThrows(Exception.class, () -> {
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(
                    "write-test-artifact", ArtifactType.OPENAPI,
                    "{}", ContentTypes.APPLICATION_JSON);
            registryClient.groups().byGroupId("default").artifacts().post(createArtifact);
        });
    }
}
