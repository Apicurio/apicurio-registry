package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.auth.SimpleAuthTest;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlAuthTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Tag;

@QuarkusTest
@TestProfile(KafkasqlAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
@Typed(KafkaSqlAuthTest.class)
public class KafkaSqlAuthTest extends SimpleAuthTest {

}
