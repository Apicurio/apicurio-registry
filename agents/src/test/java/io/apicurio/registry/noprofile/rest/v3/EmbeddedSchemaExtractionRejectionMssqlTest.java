package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.storage.util.MssqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link EmbeddedSchemaExtractionRejectionTest} against SQL Server.
 */
@QuarkusTest
@TestProfile(MssqlTestProfile.class)
public class EmbeddedSchemaExtractionRejectionMssqlTest extends EmbeddedSchemaExtractionRejectionTest {
}
