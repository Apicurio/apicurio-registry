package io.apicurio.tests.smokeTests.apicurio.kafkasql;

import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;

@Tag(Constants.SMOKE)
@QuarkusIntegrationTest
@TestProfile(KafkasqlTestProfile.class)
public class KafkasqlSnapshotTest {
}
