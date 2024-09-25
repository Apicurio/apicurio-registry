package io.apicurio.registry;

import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@QuarkusTest
public class ConfluentDataMigrationTest extends AbstractResourceTestBase {

    @Inject
    @Current
    RegistryStorage storage;

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        super.beforeAll();

        storage.deleteAllUserData();
        try (InputStream data = resourceToInputStream("./upgrade/confluent-schema-registry-export.zip")) {

            clientV3.admin().importEscaped().post(data, config -> {
                config.headers.add("X-Registry-Preserve-GlobalId", "false");
            });
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
        Assertions.assertEquals(Set.of(RuleType.VALIDITY, RuleType.COMPATIBILITY), ruleTypes);

        Assertions.assertEquals("SYNTAX_ONLY",
                clientV3.admin().rules().byRuleType("VALIDITY").get().getConfig());

        Assertions.assertEquals("BACKWARD",
                clientV3.admin().rules().byRuleType("COMPATIBILITY").get().getConfig());
    }

    @Test
    public void testCheckArtifacts() {
        ArtifactSearchResults results = clientV3.search().artifacts().get();
        Assertions.assertEquals(14, results.getArtifacts().size());
        Assertions.assertEquals(14, results.getCount());
    }

    @Test
    public void testCheckArtifact() {
        ArtifactMetaData amd = clientV3.groups().byGroupId("default").artifacts().byArtifactId("test-value")
                .get();
        Assertions.assertEquals(null, amd.getGroupId());
        Assertions.assertEquals("test-value", amd.getArtifactId());
        Assertions.assertEquals("PROTOBUF", amd.getArtifactType());
    }

    @Test
    public void testCheckArtifactReferences() {
        List<ArtifactReference> artifactReferences = clientV3.groups().byGroupId("default").artifacts()
                .byArtifactId("test-value").versions().byVersionExpression("1").references().get();

        Assertions.assertEquals(2, artifactReferences.size());
    }
}
