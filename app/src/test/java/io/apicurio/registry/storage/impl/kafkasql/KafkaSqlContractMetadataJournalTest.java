package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.impl.kafkasql.messages.TransitionContractStatus6Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateContractMetadata4Message;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verifies that the contract metadata and contract status operations are written to the KafkaSQL
 * journal, so that they are replicated to every node rather than applied only locally.
 */
@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class KafkaSqlContractMetadataJournalTest extends AbstractResourceTestBase {

    private static final String JOURNAL_TOPIC = "kafkasql-journal";
    private static final String GROUP = "default";

    @Inject
    KafkaSqlRegistryStorage kafkaSqlRegistryStorage;

    @Test
    public void testUpdateContractMetadataIsJournaled() throws Exception {
        String artifactId = "journal-metadata-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);

        String prefix = ContractLabels.contractPrefix("probe");
        Map<String, String> labels = Map.of(prefix + ContractLabels.SUFFIX_OWNER_TEAM, "probe-team");

        assertOperationIsJournaled(
                () -> kafkaSqlRegistryStorage.updateContractMetadata(null, artifactId, prefix, labels),
                UpdateContractMetadata4Message.class.getSimpleName());
    }

    @Test
    public void testTransitionContractStatusIsJournaled() throws Exception {
        String artifactId = "journal-status-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);

        String prefix = ContractLabels.contractPrefix("probe");

        assertOperationIsJournaled(
                () -> kafkaSqlRegistryStorage.transitionContractStatus(null, artifactId, "DRAFT",
                        "STABLE", prefix, "2026-01-01"),
                TransitionContractStatus6Message.class.getSimpleName());
    }

    /**
     * Runs the given storage operation and asserts that a message of the expected type is produced to
     * the KafkaSQL journal topic.
     */
    private void assertOperationIsJournaled(Runnable operation, String expectedType) {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        System.getProperty("bootstrap.servers.external"),
                        ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        try {
            consumer.subscribe(List.of(JOURNAL_TOPIC));

            operation.run();

            long deadline = System.currentTimeMillis() + 15000;
            boolean found = false;
            while (System.currentTimeMillis() < deadline && !found) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    Header header = record.headers().lastHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER);
                    if (header != null
                            && expectedType.equals(new String(header.value(), StandardCharsets.UTF_8))) {
                        found = true;
                        break;
                    }
                }
            }

            Assertions.assertTrue(found, "Expected a " + expectedType
                    + " message to be produced to the KafkaSQL journal topic, but none was found. "
                    + "This means the operation bypassed the journal and would not be replicated "
                    + "across nodes.");
        } finally {
            consumer.close();
        }
    }
}
