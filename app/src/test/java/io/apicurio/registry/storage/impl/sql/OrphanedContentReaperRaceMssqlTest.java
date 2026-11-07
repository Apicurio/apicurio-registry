package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.MssqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(MssqlTestProfile.class)
@Typed(OrphanedContentReaperRaceMssqlTest.class)
@DisabledOnOs(value = OS.MAC, disabledReason = "the SQL Server container image crashes on Apple silicon")
public class OrphanedContentReaperRaceMssqlTest extends OrphanedContentReaperRaceTest {
}
