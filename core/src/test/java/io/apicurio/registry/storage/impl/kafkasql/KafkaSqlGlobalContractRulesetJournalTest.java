package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteGlobalContractRuleset0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.SetGlobalContractRuleset1Message;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class KafkaSqlGlobalContractRulesetJournalTest extends AbstractResourceTestBase {

    private static final String JOURNAL_TOPIC = "kafkasql-journal";

    @Inject
    KafkaSqlRegistryStorage kafkaSqlRegistryStorage;

    @Test
    public void testSetGlobalContractRulesetIsJournaled() {
        ContractRuleSetDto ruleset = ContractRuleSetDto.builder().domainRules(List.of())
                .migrationRules(List.of()).build();
        assertOperationIsJournaled(() -> kafkaSqlRegistryStorage.setGlobalContractRuleset(ruleset),
                SetGlobalContractRuleset1Message.class.getSimpleName());
    }

    @Test
    public void testDeleteGlobalContractRulesetIsJournaled() {
        assertOperationIsJournaled(() -> kafkaSqlRegistryStorage.deleteGlobalContractRuleset(),
                DeleteGlobalContractRuleset0Message.class.getSimpleName());
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
                    + "across nodes (issue #8848).");
        } finally {
            consumer.close();
        }
    }
}
