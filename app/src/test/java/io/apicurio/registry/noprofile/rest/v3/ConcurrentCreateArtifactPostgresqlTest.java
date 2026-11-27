package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(PostgresqlTestProfile.class)
@Typed(ConcurrentCreateArtifactPostgresqlTest.class)
public class ConcurrentCreateArtifactPostgresqlTest extends ConcurrentCreateArtifactTest {
}
