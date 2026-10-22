package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.storage.dto.ContractAuditEntryDto;
import io.apicurio.registry.storage.impl.kafkasql.messages.InsertContractAuditEntry1Message;
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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class KafkaSqlContractAuditJournalTest extends AbstractResourceTestBase {

    private static final String JOURNAL_TOPIC = "kafkasql-journal";

    @Inject
    KafkaSqlRegistryStorage kafkaSqlRegistryStorage;

    @Test
    public void testInsertContractAuditEntryIsJournaled() {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        System.getProperty("bootstrap.servers.external"),
                        ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        try {
            consumer.subscribe(List.of(JOURNAL_TOPIC));

            ContractAuditEntryDto entry = ContractAuditEntryDto.builder().groupId("audit-group")
                    .artifactId("audit-artifact").action("METADATA_UPDATED").principal("test-principal")
                    .createdOn(new Date()).build();
            kafkaSqlRegistryStorage.insertContractAuditEntry(entry);

            String expectedType = InsertContractAuditEntry1Message.class.getSimpleName();
            long deadline = System.currentTimeMillis() + 15000;
            boolean found = false;
            while (System.currentTimeMillis() < deadline && !found) {
                for (ConsumerRecord<String, String> consumerRecord : consumer.poll(Duration.ofMillis(500))) {
                    Header header = consumerRecord.headers().lastHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER);
                    if (header != null
                            && expectedType.equals(new String(header.value(), StandardCharsets.UTF_8))) {
                        found = true;
                        break;
                    }
                }
            }

            Assertions.assertTrue(found, "Expected an " + expectedType
                    + " message to be produced to the KafkaSQL journal topic, but none was found. "
                    + "This means the contract audit entry bypassed the journal and would not be "
                    + "replicated across nodes (issue #8878).");
        } finally {
            consumer.close();
        }
    }
}
