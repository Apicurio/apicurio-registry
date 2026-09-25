package io.apicurio.registry.noprofile.rest.a2a;

import io.apicurio.registry.utils.tests.KafkaTestContainerManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import java.util.List;
import java.util.Map;

/** Runs the same authenticated governance/lifecycle regressions through the Kafka journal. */
@QuarkusTest
@TestProfile(OpenApiAgentCardKafkaSqlTest.Profile.class)
class OpenApiAgentCardKafkaSqlTest extends OpenApiAgentCardSafetyTest {
    public static class Profile extends OpenApiAgentCardSafetyTest.Profile {
        @Override
        public Map<String, String> getConfigOverrides() {
            var config = super.getConfigOverrides();
            config.put("apicurio.storage.kind", "kafkasql");
            return config;
        }

        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(KafkaTestContainerManager.class));
        }
    }
}
