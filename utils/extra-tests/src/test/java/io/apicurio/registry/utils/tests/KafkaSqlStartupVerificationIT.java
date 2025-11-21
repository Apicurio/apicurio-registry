package io.apicurio.registry.utils.tests;

import io.apicurio.registry.utils.tests.infra.KafkaInfra;
import io.apicurio.registry.utils.tests.infra.RegistryV2KafkaSQLInfra;
import io.apicurio.registry.utils.tests.infra.RegistryV3KafkaSQLInfra;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaSqlStartupVerificationIT {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlStartupVerificationIT.class);

    private static final KafkaInfra kafka = KafkaInfra.getInstance();
    private static final RegistryV2KafkaSQLInfra registry2 = RegistryV2KafkaSQLInfra.getInstance();
    private static final RegistryV3KafkaSQLInfra registry3 = RegistryV3KafkaSQLInfra.getInstance();

    @BeforeAll
    static void beforeAll() {
        kafka.start();
    }

    @Test
    public void testHappyPath() {
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isTrue();
        registry3.stop();
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isTrue();
    }

    @Test
    public void testTopicReuse() {
        assertThat(registry2.start(kafka.getNetworkBootstrapServers())).isTrue();
        registry2.stop();
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isFalse();
    }

    @Test
    public void testGoodJournalTopicConfig1() {
        kafka.createTopic("kafkasql-journal", null, null, Map.of(
                "cleanup.policy", "delete",
                "retention.ms", "-1",
                "retention.bytes", "-1"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isTrue();
    }

    @Test
    public void testBadJournalTopicConfig1() {
        kafka.createTopic("kafkasql-journal", null, null, Map.of(
                "cleanup.policy", "compact"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isFalse();
    }

    @Test
    public void testBadJournalTopicConfig2() {
        kafka.createTopic("kafkasql-journal", null, null, Map.of(
                "cleanup.policy", "delete",
                "retention.ms", "42000"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isFalse();
    }

    @Test
    public void testBadJournalTopicConfig3() {
        kafka.createTopic("kafkasql-journal", null, null, Map.of(
                "cleanup.policy", "delete",
                "retention.bytes", "42000"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isFalse();
    }

    @Test
    public void testGoodSnapshotsTopicConfig1() {
        kafka.createTopic("kafkasql-snapshots", null, null, Map.of(
                "cleanup.policy", "delete",
                "retention.ms", "-1",
                "retention.bytes", "-1"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isTrue();
    }

    @Test
    public void testBadSnapshotsTopicConfig1() {
        kafka.createTopic("kafkasql-snapshots", null, null, Map.of(
                "cleanup.policy", "compact"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isFalse();
    }

    @Test
    public void testBadSnapshotsTopicConfig2() {
        kafka.createTopic("kafkasql-snapshots", null, null, Map.of(
                "cleanup.policy", "delete",
                "retention.ms", "42000"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isFalse();
    }

    @Test
    public void testBadSnapshotsTopicConfig2Override() {
        kafka.createTopic("kafkasql-snapshots", null, null, Map.of(
                "cleanup.policy", "delete",
                "retention.ms", "42000"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers(),
                Map.of("APICURIO_KAFKASQL_TOPIC_CONFIGURATION_VERIFICATION_OVERRIDE_ENABLED", "true"))
        ).isTrue();
    }

    @Test
    public void testBadSnapshotsTopicConfig3() {
        kafka.createTopic("kafkasql-snapshots", null, null, Map.of(
                "cleanup.policy", "delete",
                "retention.bytes", "42000"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isFalse();
    }

    @Test
    public void testGoodEventsTopicConfig1() {
        // Events topic is allowed to use retention
        kafka.createTopic("registry-events", null, null, Map.of(
                "cleanup.policy", "delete",
                "retention.ms", "42000",
                "retention.bytes", "42000"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isTrue();
    }

    @Test
    public void testBadEventsTopicConfig1() {
        // Events topic is allowed to use retention
        kafka.createTopic("registry-events", null, null, Map.of(
                "cleanup.policy", "compact"
        ));
        assertThat(registry3.start(kafka.getNetworkBootstrapServers())).isFalse();
    }

    @AfterEach
    void afterEach() {
        registry3.stop();
        registry2.stop();
        kafka.getAdminClient().deleteTopics(Set.of("kafkasql-journal", "kafkasql-snapshots", "registry-events"));
    }

    @AfterAll
    static void afterAll() {
        kafka.stop();
    }
}