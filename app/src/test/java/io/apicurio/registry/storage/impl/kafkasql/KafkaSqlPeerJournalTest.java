package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreatePeer1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeletePeer1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdatePeer1Message;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Verifies that peer registry operations are written to the KafkaSQL journal, so that they are
 * replicated to every node rather than applied only locally.
 */
@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class KafkaSqlPeerJournalTest extends AbstractResourceTestBase {

    private static final String JOURNAL_TOPIC = "kafkasql-journal";

    @Inject
    KafkaSqlRegistryStorage kafkaSqlRegistryStorage;

    @Test
    public void testCreatePeerIsJournaledWithoutCredentialValue() throws Exception {
        String peerId = "journal-peer-create-" + UUID.randomUUID();
        PeerDto peer = PeerDto.builder().peerId(peerId).url("https://peer.example.com")
                .name("Journal Peer").enabled(true).credentialSecretRef("journal-peer-cred").build();

        String recordValue = assertOperationIsJournaled(() -> kafkaSqlRegistryStorage.createPeer(peer),
                CreatePeer1Message.class.getSimpleName());

        assertNoCredentialValue(recordValue);

        kafkaSqlRegistryStorage.deletePeer(peerId);
    }

    @Test
    public void testUpdatePeerIsJournaledWithoutCredentialValue() throws Exception {
        String peerId = "journal-peer-update-" + UUID.randomUUID();
        PeerDto peer = PeerDto.builder().peerId(peerId).url("https://peer.example.com")
                .enabled(true).credentialSecretRef("journal-peer-cred").build();
        kafkaSqlRegistryStorage.createPeer(peer);

        PeerDto updated = PeerDto.builder().peerId(peerId).url("https://peer-updated.example.com")
                .enabled(false).credentialSecretRef("journal-peer-cred-updated").build();

        String recordValue = assertOperationIsJournaled(() -> kafkaSqlRegistryStorage.updatePeer(updated),
                UpdatePeer1Message.class.getSimpleName());

        assertNoCredentialValue(recordValue);

        kafkaSqlRegistryStorage.deletePeer(peerId);
    }

    @Test
    public void testDeletePeerIsJournaled() throws Exception {
        String peerId = "journal-peer-delete-" + UUID.randomUUID();
        PeerDto peer = PeerDto.builder().peerId(peerId).url("https://peer.example.com").enabled(true)
                .build();
        kafkaSqlRegistryStorage.createPeer(peer);

        assertOperationIsJournaled(() -> kafkaSqlRegistryStorage.deletePeer(peerId),
                DeletePeer1Message.class.getSimpleName());
    }

    private void assertNoCredentialValue(String recordValue) {
        Assertions.assertTrue(recordValue.contains("journal-peer-cred"),
                "Expected the journalled message to contain the credential reference key.");
        String lower = recordValue.toLowerCase(Locale.ROOT);
        Assertions.assertFalse(lower.contains("credentialvalue"),
                "The journalled message must never carry a credential value, only a reference.");
        Assertions.assertFalse(lower.contains("password"),
                "The journalled message must never carry a credential value, only a reference.");
    }

    /**
     * Runs the given storage operation and asserts that a message of the expected type is produced to
     * the KafkaSQL journal topic. Returns the string value of the matched record.
     */
    private String assertOperationIsJournaled(Runnable operation, String expectedType) {
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
            String foundValue = null;
            while (System.currentTimeMillis() < deadline && foundValue == null) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    Header header = record.headers().lastHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER);
                    if (header != null
                            && expectedType.equals(new String(header.value(), StandardCharsets.UTF_8))) {
                        foundValue = record.value();
                        break;
                    }
                }
            }

            Assertions.assertNotNull(foundValue, "Expected a " + expectedType
                    + " message to be produced to the KafkaSQL journal topic, but none was found. "
                    + "This means the operation bypassed the journal and would not be replicated "
                    + "across nodes.");
            return foundValue;
        } finally {
            consumer.close();
        }
    }
}
