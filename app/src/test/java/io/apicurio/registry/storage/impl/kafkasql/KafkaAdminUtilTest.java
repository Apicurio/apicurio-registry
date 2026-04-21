package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlFactory.KafkaSqlVerificationJournalConsumer;
import io.apicurio.registry.storage.impl.util.KafkaAdminUtil;
import jakarta.enterprise.inject.Instance;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KafkaAdminUtilTest {

    private static final String TEST_TOPIC = "test-journal-topic";
    private static final TopicPartition TP0 = new TopicPartition(TEST_TOPIC, 0);

    private KafkaAdminUtil kafkaAdminUtil;
    private KafkaConsumer<Bytes, Bytes> consumer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        consumer = mock(KafkaConsumer.class);

        KafkaSqlVerificationJournalConsumer verificationResource =
                new KafkaSqlVerificationJournalConsumer(() -> consumer);

        Instance<KafkaSqlVerificationJournalConsumer> verificationInstance = mock(Instance.class);
        when(verificationInstance.get()).thenReturn(verificationResource);

        KafkaSqlConfiguration config = mock(KafkaSqlConfiguration.class);
        when(config.getPollTimeout()).thenReturn(Duration.ofMillis(100));
        when(config.getTopic()).thenReturn(TEST_TOPIC);

        Instance<KafkaSqlConfiguration> configInstance = mock(Instance.class);
        when(configInstance.get()).thenReturn(config);

        kafkaAdminUtil = new KafkaAdminUtil();
        setField("log", LoggerFactory.getLogger(KafkaAdminUtil.class));
        setField("verificationConsumer", verificationInstance);
        setField("configuration", configInstance);
    }

    /**
     * Verifies that the verification loop terminates even when the topic keeps receiving new
     * messages from other pods. Without the end-offset bound, the while(true) loop would poll
     * indefinitely because records.isEmpty() is never true.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testVerifyTerminatesWithConcurrentProducers() {
        long endOffset = 10L;

        // Partition assignment: empty on first call, then assigned
        when(consumer.assignment())
                .thenReturn(Collections.emptySet())
                .thenReturn(Set.of(TP0));

        when(consumer.endOffsets(Set.of(TP0)))
                .thenReturn(Map.of(TP0, endOffset));

        // Poll sequence:
        //   1st: empty (triggers partition assignment)
        //   2nd: 10 records (offsets 0-9, reaches endOffset)
        //   3rd: would return more records from concurrent producers — should NOT be reached
        when(consumer.poll(any(Duration.class)))
                .thenReturn(ConsumerRecords.empty())
                .thenReturn(createRecords(0, 10))
                .thenThrow(new AssertionError("Poll called beyond original end offset"));

        // After consuming the first batch, position equals the original end offset
        when(consumer.position(TP0)).thenReturn(endOffset);

        kafkaAdminUtil.verifyJournalTopicContents();

        // 2 polls: one during assignment wait, one in the verification loop
        verify(consumer, times(2)).poll(any(Duration.class));
    }

    /**
     * Verifies that the loop still terminates via empty poll when there are no concurrent
     * producers (the original behavior is preserved).
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testVerifyTerminatesOnEmptyPoll() {
        long endOffset = 5L;

        when(consumer.assignment())
                .thenReturn(Collections.emptySet())
                .thenReturn(Set.of(TP0));

        when(consumer.endOffsets(Set.of(TP0)))
                .thenReturn(Map.of(TP0, endOffset));

        // Poll: assignment poll, then records, then empty (no concurrent producers)
        when(consumer.poll(any(Duration.class)))
                .thenReturn(ConsumerRecords.empty())
                .thenReturn(createRecords(0, 5))
                .thenReturn(ConsumerRecords.empty());

        // Position hasn't quite reached endOffset, but the next poll is empty so the loop
        // breaks via the isEmpty() check before reaching the position check.
        when(consumer.position(TP0)).thenReturn(3L);

        kafkaAdminUtil.verifyJournalTopicContents();

        // 3 polls: assignment, records, empty
        verify(consumer, times(3)).poll(any(Duration.class));
    }

    /**
     * Verifies that on an empty topic (endOffset == 0), the loop terminates immediately.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testVerifyTerminatesOnEmptyTopic() {
        when(consumer.assignment())
                .thenReturn(Collections.emptySet())
                .thenReturn(Set.of(TP0));

        when(consumer.endOffsets(Set.of(TP0)))
                .thenReturn(Map.of(TP0, 0L));

        when(consumer.poll(any(Duration.class)))
                .thenReturn(ConsumerRecords.empty());

        when(consumer.position(TP0)).thenReturn(0L);

        kafkaAdminUtil.verifyJournalTopicContents();

        // 2 polls: assignment + one empty poll in the loop
        verify(consumer, times(2)).poll(any(Duration.class));
    }

    private ConsumerRecords<Bytes, Bytes> createRecords(long startOffset, int count) {
        List<ConsumerRecord<Bytes, Bytes>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new ConsumerRecord<>(
                    TEST_TOPIC, 0, startOffset + i,
                    new Bytes(new byte[]{1}), new Bytes(new byte[]{2})));
        }
        return new ConsumerRecords<>(Map.of(TP0, records));
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = KafkaAdminUtil.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(kafkaAdminUtil, value);
    }
}
