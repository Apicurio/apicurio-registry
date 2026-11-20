package io.apicurio.registry.agents.services;

import io.apicurio.registry.storage.util.MssqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link EmbeddedSchemaServiceTest} against SQL Server.
 */
@QuarkusTest
@TestProfile(MssqlTestProfile.class)
public class EmbeddedSchemaServiceMssqlTest extends EmbeddedSchemaServiceTest {
}
