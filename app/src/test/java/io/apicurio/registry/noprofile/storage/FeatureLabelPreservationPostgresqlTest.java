package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(PostgresqlTestProfile.class)
public class FeatureLabelPreservationPostgresqlTest extends FeatureLabelPreservationTest {
}
