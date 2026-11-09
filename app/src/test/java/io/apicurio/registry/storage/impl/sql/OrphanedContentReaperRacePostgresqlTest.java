package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(PostgresqlTestProfile.class)
@Typed(OrphanedContentReaperRacePostgresqlTest.class)
public class OrphanedContentReaperRacePostgresqlTest extends OrphanedContentReaperRaceTest {
}
