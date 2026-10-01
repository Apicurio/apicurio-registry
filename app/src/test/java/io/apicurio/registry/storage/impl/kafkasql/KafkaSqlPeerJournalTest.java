package io.apicurio.registry.storage.impl.kafkasql;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.error.InvalidPeerException;
import io.apicurio.registry.storage.error.PeerNotFoundException;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreatePeer1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeletePeer1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdatePeer1Message;
import io.apicurio.registry.util.JsonObjectMapper;
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
import java.util.ArrayList;
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
        try {
            String recordValue = assertOperationIsJournaled(() -> kafkaSqlRegistryStorage.createPeer(peer),
                    CreatePeer1Message.class.getSimpleName(), peerId);

            assertNoCredentialValue(recordValue);

            PeerDto decoded = decodePeerPayload(recordValue);
            Assertions.assertEquals(peerId, decoded.getPeerId());
            Assertions.assertEquals("https://peer.example.com", decoded.getUrl());
            Assertions.assertEquals("Journal Peer", decoded.getName());
            Assertions.assertTrue(decoded.isEnabled());
            Assertions.assertEquals("journal-peer-cred", decoded.getCredentialSecretRef());
        } finally {
            deletePeerIgnoringNotFound(peerId);
        }
    }

    @Test
    public void testUpdatePeerIsJournaledWithoutCredentialValue() throws Exception {
        String peerId = "journal-peer-update-" + UUID.randomUUID();
        PeerDto peer = PeerDto.builder().peerId(peerId).url("https://peer.example.com")
                .enabled(true).credentialSecretRef("journal-peer-cred").build();
        kafkaSqlRegistryStorage.createPeer(peer);
        try {
            PeerDto updated = PeerDto.builder().peerId(peerId).url("https://peer-updated.example.com")
                    .enabled(false).credentialSecretRef("journal-peer-cred-updated").build();

            String recordValue = assertOperationIsJournaled(() -> kafkaSqlRegistryStorage.updatePeer(updated),
                    UpdatePeer1Message.class.getSimpleName(), peerId);

            assertNoCredentialValue(recordValue);

            PeerDto decoded = decodePeerPayload(recordValue);
            Assertions.assertEquals(peerId, decoded.getPeerId());
            Assertions.assertEquals("https://peer-updated.example.com", decoded.getUrl());
            Assertions.assertFalse(decoded.isEnabled());
            Assertions.assertEquals("journal-peer-cred-updated", decoded.getCredentialSecretRef());
        } finally {
            deletePeerIgnoringNotFound(peerId);
        }
    }

    @Test
    public void testDeletePeerIsJournaled() throws Exception {
        String peerId = "journal-peer-delete-" + UUID.randomUUID();
        PeerDto peer = PeerDto.builder().peerId(peerId).url("https://peer.example.com").enabled(true)
                .build();
        kafkaSqlRegistryStorage.createPeer(peer);
        try {
            String recordValue = assertOperationIsJournaled(() -> kafkaSqlRegistryStorage.deletePeer(peerId),
                    DeletePeer1Message.class.getSimpleName(), peerId);
            Assertions.assertEquals(peerId, extractPeerId(recordValue));
        } finally {
            deletePeerIgnoringNotFound(peerId);
        }
    }

    @Test
    public void testInvalidPeerIdIsRejectedBeforeJournalSubmission() throws Exception {
        String peerId = "journal-peer-case-" + UUID.randomUUID();
        String uppercasePeerId = peerId.toUpperCase(Locale.ROOT);
        kafkaSqlRegistryStorage.createPeer(PeerDto.builder().peerId(peerId).url("https://peer.example.com")
                .enabled(true).build());
        try {
            PeerDto uppercasePeer = PeerDto.builder().peerId(uppercasePeerId)
                    .url("https://peer-updated.example.com").enabled(true).build();
            Assertions.assertThrows(InvalidPeerException.class,
                    () -> kafkaSqlRegistryStorage.deletePeer(uppercasePeerId));
            Assertions.assertThrows(InvalidPeerException.class,
                    () -> kafkaSqlRegistryStorage.createPeer(uppercasePeer));
            Assertions.assertThrows(InvalidPeerException.class,
                    () -> kafkaSqlRegistryStorage.updatePeer(uppercasePeer));
            Assertions.assertEquals("https://peer.example.com", kafkaSqlRegistryStorage.getPeer(peerId).getUrl());

            // Every journal message uses the same partition key, so anything the rejected calls
            // had submitted would be read before this delete.
            List<ConsumerRecord<String, String>> journal = readJournalThrough(
                    () -> kafkaSqlRegistryStorage.deletePeer(peerId), DeletePeer1Message.class.getSimpleName(),
                    peerId);

            Assertions.assertTrue(journal.stream()
                    .anyMatch(r -> CreatePeer1Message.class.getSimpleName().equals(messageType(r))
                            && peerId.equals(extractPeerId(r.value()))),
                    "Expected the journal read to include the earlier create of '" + peerId + "'.");
            Assertions.assertTrue(journal.stream().noneMatch(r -> uppercasePeerId.equals(extractPeerId(r.value()))),
                    "A peer operation with an invalid id reached the KafkaSQL journal.");
            Assertions.assertFalse(kafkaSqlRegistryStorage.isPeerExists(peerId));
        } finally {
            deletePeerIgnoringNotFound(peerId);
        }
    }

    private void deletePeerIgnoringNotFound(String peerId) {
        try {
            kafkaSqlRegistryStorage.deletePeer(peerId);
        } catch (PeerNotFoundException ignored) {
            // Already removed by the test's own action (e.g. testDeletePeerIsJournaled), or
            // never successfully created because the operation under test failed. Either way
            // there is nothing left to clean up.
        }
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
     * Extracts the {@code peerId} from a journalled message payload, whether it appears at the
     * top level ({@code DeletePeer1Message}) or nested under {@code peer} ({@code CreatePeer1Message},
     * {@code UpdatePeer1Message}). Returns null if the payload cannot be parsed or carries neither.
     */
    private String extractPeerId(String recordValue) {
        try {
            JsonNode root = JsonObjectMapper.MAPPER.readTree(recordValue);
            JsonNode direct = root.get("peerId");
            if (direct != null && !direct.isNull()) {
                return direct.asText();
            }
            JsonNode nested = root.path("peer").get("peerId");
            return nested != null && !nested.isNull() ? nested.asText() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private PeerDto decodePeerPayload(String recordValue) throws Exception {
        JsonNode root = JsonObjectMapper.MAPPER.readTree(recordValue);
        return JsonObjectMapper.MAPPER.treeToValue(root.path("peer"), PeerDto.class);
    }

    /**
     * Runs the given storage operation and asserts that a message of the expected type, carrying
     * the expected peer id, is produced to the KafkaSQL journal topic. The journal is shared
     * across every test in the class (and every other KafkaSQL test running in the same broker),
     * so matching on message type alone is not enough: a create test could match a create from a
     * different method, and a delete test could match a cleanup deletion left over from another
     * test even if its own delete is never journaled. Matching requires both the message type
     * header and the peer id decoded from the payload. Returns the string value of the matched
     * record.
     */
    private String assertOperationIsJournaled(Runnable operation, String expectedType, String expectedPeerId) {
        List<ConsumerRecord<String, String>> records = readJournalThrough(operation, expectedType, expectedPeerId);
        return records.get(records.size() - 1).value();
    }

    /**
     * Runs the given storage operation and reads the KafkaSQL journal from the beginning until the
     * message produced by that operation is found, as matched by
     * {@link #assertOperationIsJournaled(Runnable, String, String)}. Returns every record read, in
     * journal order, ending with the matched record.
     */
    private List<ConsumerRecord<String, String>> readJournalThrough(Runnable operation, String expectedType,
            String expectedPeerId) {
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
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            boolean found = false;
            while (System.currentTimeMillis() < deadline && !found) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    records.add(record);
                    if (expectedType.equals(messageType(record))
                            && expectedPeerId.equals(extractPeerId(record.value()))) {
                        found = true;
                        break;
                    }
                }
            }

            Assertions.assertTrue(found, "Expected a " + expectedType + " message for peer '"
                    + expectedPeerId + "' to be produced to the KafkaSQL journal topic, but none was "
                    + "found. This means the operation bypassed the journal and would not be "
                    + "replicated across nodes.");
            return records;
        } finally {
            consumer.close();
        }
    }

    private String messageType(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
