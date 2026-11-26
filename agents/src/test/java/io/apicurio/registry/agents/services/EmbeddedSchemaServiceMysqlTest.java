package io.apicurio.registry.agents.services;

import io.apicurio.registry.storage.util.MysqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link EmbeddedSchemaServiceTest} against MySQL.
 */
@QuarkusTest
@TestProfile(MysqlTestProfile.class)
public class EmbeddedSchemaServiceMysqlTest extends EmbeddedSchemaServiceTest {
}
