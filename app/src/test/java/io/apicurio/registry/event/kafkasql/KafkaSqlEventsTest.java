package io.apicurio.registry.event.kafkasql;

import io.apicurio.registry.event.sql.RegistryEventsTest;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

import java.util.List;

@QuarkusTest
@TestProfile(KafkaSqlEventsTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
@Typed(KafkaSqlEventsTest.class)
public class KafkaSqlEventsTest extends RegistryEventsTest {

    @BeforeAll
    @Override
    public void init() {
        consumer = getConsumer(System.getProperty("bootstrap.servers.external"));
        consumer.subscribe(List.of("registry-events"));
    }
}
