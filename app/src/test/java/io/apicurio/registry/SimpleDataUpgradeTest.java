package io.apicurio.registry;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
public class SimpleDataUpgradeTest extends AbstractResourceTestBase {

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        super.beforeAll();

        try (InputStream data = resourceToInputStream("./upgrade/v2_export_simple.zip")) {
            clientV3.admin().importEscaped().post(data);
        }
    }

    @BeforeEach
    public void beforeEach() {
        // Do nothing, but override the super class because it deletes all global rules.
    }

    @Test
    public void testCheckGlobalRules() {
        Set<RuleType> ruleTypes = new HashSet<>(clientV3.admin().rules().get());
        Assertions.assertEquals(2, ruleTypes.size());
        Assertions.assertEquals(Set.of(RuleType.VALIDITY, RuleType.INTEGRITY), ruleTypes);
        Assertions.assertEquals("SYNTAX_ONLY",
                clientV3.admin().rules().byRuleType("VALIDITY").get().getConfig());
        Assertions.assertEquals("NO_DUPLICATES",
                clientV3.admin().rules().byRuleType("INTEGRITY").get().getConfig());
    }

    @Test
    public void testCheckGroups() {
        GroupSearchResults results = clientV3.groups().get();
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("TestGroup1", results.getGroups().get(0).getGroupId());

        GroupMetaData gmd = clientV3.groups().byGroupId("TestGroup1").get();
        Assertions.assertEquals("TestGroup1", gmd.getGroupId());
        Assertions.assertNull(gmd.getLabels());
        Assertions.assertNull(gmd.getDescription());

        List<RuleType> rules = clientV3.groups().byGroupId("TestGroup1").rules().get();
        Assertions.assertEquals(0, rules.size());
    }

    @Test
    public void testCheckArtifacts() {
        ArtifactSearchResults results = clientV3.search().artifacts().get();
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("TestGroup1", results.getArtifacts().get(0).getGroupId());
        Assertions.assertEquals("TestArtifact1", results.getArtifacts().get(0).getArtifactId());
        Assertions.assertEquals("Version Two", results.getArtifacts().get(0).getName());
        Assertions.assertEquals("The first test artifact, but the second version of it.",
                results.getArtifacts().get(0).getDescription());

        results = clientV3.groups().byGroupId("TestGroup1").artifacts().get();
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals("TestArtifact1", results.getArtifacts().get(0).getArtifactId());
        Assertions.assertEquals("Version Two", results.getArtifacts().get(0).getName());
        Assertions.assertEquals("The first test artifact, but the second version of it.",
                results.getArtifacts().get(0).getDescription());
    }

    @Test
    public void testCheckArtifact() {
        ArtifactMetaData amd = clientV3.groups().byGroupId("TestGroup1").artifacts()
                .byArtifactId("TestArtifact1").get();
        Assertions.assertEquals("TestGroup1", amd.getGroupId());
        Assertions.assertEquals("TestArtifact1", amd.getArtifactId());
        Assertions.assertEquals("AVRO", amd.getArtifactType());
        Assertions.assertEquals("Version Two", amd.getName());
        Assertions.assertEquals("The first test artifact, but the second version of it.",
                amd.getDescription());
        Assertions.assertEquals(Map.of("zip", "rar", "foo", "bar"), amd.getLabels().getAdditionalData());
    }

    @Test
    public void testCheckArtifactVersions() {
        VersionSearchResults results = clientV3.groups().byGroupId("TestGroup1").artifacts()
                .byArtifactId("TestArtifact1").versions().get();
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals(2, results.getVersions().size());

        VersionMetaData version1 = clientV3.groups().byGroupId("TestGroup1").artifacts()
                .byArtifactId("TestArtifact1").versions().byVersionExpression("1").get();
        Assertions.assertEquals("TestArtifact1", version1.getArtifactId());
        Assertions.assertEquals("TestGroup1", version1.getGroupId());
        Assertions.assertEquals("1", version1.getVersion());
        Assertions.assertEquals("Test Artifact One", version1.getName());
        Assertions.assertEquals("The first test artifact.", version1.getDescription());
        Assertions.assertEquals(Map.of("foo", "bar", "zip", ""), version1.getLabels().getAdditionalData());

        VersionMetaData version2 = clientV3.groups().byGroupId("TestGroup1").artifacts()
                .byArtifactId("TestArtifact1").versions().byVersionExpression("2").get();
        Assertions.assertEquals("TestArtifact1", version2.getArtifactId());
        Assertions.assertEquals("TestGroup1", version2.getGroupId());
        Assertions.assertEquals("2", version2.getVersion());
        Assertions.assertEquals("Version Two", version2.getName());
        Assertions.assertEquals("The first test artifact, but the second version of it.",
                version2.getDescription());
        Assertions.assertEquals(Map.of("foo", "bar", "zip", "rar"), version2.getLabels().getAdditionalData());
    }

}
