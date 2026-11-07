package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.POSITION_AT_JOURNAL_END;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSnapshotRestoreTestResource.SCENARIO;
import static org.awaitility.Awaitility.await;

/**
 * Restores a snapshot whose record points at the offset the journal ends at once the registry has
 * appended its bootstrap record. A seek there is accepted, but the idle journal returns no record, so
 * the check of the first record never runs and the registry never becomes ready. It must notice that
 * the position is not inside the journal and read the journal from the beginning instead.
 */
@QuarkusTest
@TestProfile(KafkaSqlSnapshotMarkerAtJournalEndTest.PositionAtJournalEndProfile.class)
@Tag(ApicurioTestTags.SLOW)
@DisabledIfSystemProperty(named = "cluster.tests", matches = "true")
public class KafkaSqlSnapshotMarkerAtJournalEndTest extends AbstractKafkaSqlSnapshotRestoreTest {

    @ConfigProperty(name = "apicurio.kafkasql.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "apicurio.kafkasql.topic")
    String journalTopic;

    @ConfigProperty(name = "apicurio.kafkasql.snapshots.topic")
    String snapshotsTopic;

    /**
     * The scenario predicts the journal end from how many records the registry appends before it
     * subscribes. If that prediction drifts, the position lands on a record and the restore test
     * passes through a different fallback, so check that the recorded position is the journal end.
     */
    @Test
    public void testRecordedPositionIsTheJournalEnd() throws Exception {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> Assertions.assertTrue(storage.isReady(), "KafkaSQL storage did not bootstrap"));

        TopicPartition journal = new TopicPartition(journalTopic, 0);
        long journalEnd;
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            journalEnd = admin.listOffsets(Map.of(journal, OffsetSpec.latest())).partitionResult(journal)
                    .get(30, TimeUnit.SECONDS).offset();
        }

        TopicPartition snapshots = new TopicPartition(snapshotsTopic, 0);
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.assign(List.of(snapshots));
            consumer.seekToBeginning(List.of(snapshots));
            // The seeded record is already on the topic; polling only waits for the first fetch.
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
                Assertions.assertFalse(records.isEmpty(), "The seeded snapshot record was not read back");
            });
        }

        Assertions.assertEquals(new JournalPosition(0, journalEnd),
                JournalPosition.fromHeaders(records.get(0).headers()));
    }

    public static class PositionAtJournalEndProfile extends KafkasqlTestProfile {
        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(KafkaSqlSnapshotRestoreTestResource.class,
                    Map.of(SCENARIO, POSITION_AT_JOURNAL_END)));
        }
    }
}
