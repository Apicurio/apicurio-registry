package io.apicurio.tests.kafkasql;

import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@Tag(Constants.KAFKA_SQL_SNAPSHOTTING)
public class KafkaSqlSnapshottingIT extends ApicurioRegistryBaseIT {

    private static final String NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID = "SNAPSHOT_TEST_GROUP_ID";

    @Override
    @BeforeEach
    public void cleanArtifacts() throws Exception {
    }

    @Test
    public void testRecoverFromSnapshot() throws InterruptedException {
        //We expect 1000 artifacts to be present in the snapshots group, created before the snapshot.
        Assertions.assertEquals(1000, registryClient.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().get().getCount());

        //And another 1000 in the default group, created after the snapshot.
        Assertions.assertEquals(1000, registryClient.groups().byGroupId(NEW_ARTIFACTS_SNAPSHOT_TEST_GROUP_ID).artifacts().get().getCount());
    }
}