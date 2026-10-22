package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.utils.tests.KafkaTestContainerManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import java.util.List;
import java.util.Map;

@QuarkusTest
@TestProfile(StructuredContentSearchKafkaSqlTest.Profile.class)
class StructuredContentSearchKafkaSqlTest extends StructuredContentSearchTest {
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String,String> getConfigOverrides() { return Map.of("apicurio.storage.kind","kafkasql"); }
        @Override
        public List<TestResourceEntry> testResources() { return List.of(new TestResourceEntry(KafkaTestContainerManager.class)); }
    }
}
