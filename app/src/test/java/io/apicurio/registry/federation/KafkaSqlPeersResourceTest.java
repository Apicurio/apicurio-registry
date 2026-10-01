package io.apicurio.registry.federation;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;

@QuarkusTest
@TestProfile(FederationKafkasqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class KafkaSqlPeersResourceTest extends AbstractPeersResourceTest {
}
