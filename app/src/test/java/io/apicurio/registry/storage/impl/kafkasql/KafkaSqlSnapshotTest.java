package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class KafkaSqlSnapshotTest extends AbstractResourceTestBase {

    private static final String NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID = "SNAPSHOT_TEST_GROUP_ID";

    @Inject
    KafkaSqlRegistryStorage kafkaSqlRegistryStorage;

    @BeforeAll
    public void init() {
        //Create a bunch of artifacts and rules, so they're added on top of the snapshot.
        String simpleAvro = resourceToString("avro.json");

        for (int idx = 0; idx < 1000; idx++) {
            System.out.println("Iteration: " + idx);
            String artifactId = UUID.randomUUID().toString();
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO, simpleAvro,
                    ContentTypes.APPLICATION_JSON);
            clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts()
                    .post(createArtifact, config -> config.headers.add("X-Registry-ArtifactId", artifactId));
            Rule rule = new Rule();
            rule.setType(RuleType.VALIDITY);
            rule.setConfig("SYNTAX_ONLY");
            clientV3.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().byArtifactId(artifactId).rules().post(rule);
        }
    }

    @Test
    public void testSnapshotCreation() throws IOException {
        String snapshotLocation = kafkaSqlRegistryStorage.triggerSnapshotCreation();
        Path path = Path.of(snapshotLocation);
        Assertions.assertTrue(Files.exists(path));
        Files.delete(path);
    }
}
