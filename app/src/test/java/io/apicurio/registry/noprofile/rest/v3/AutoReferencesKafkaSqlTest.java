package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;

/**
 * Runs {@link AutoReferencesTest} against KafkaSQL storage.
 */
@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AutoReferencesKafkaSqlTest extends AutoReferencesTest {
}
