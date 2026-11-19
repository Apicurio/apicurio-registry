package io.apicurio.registry.storage;

import io.apicurio.registry.exception.RuntimeAssertionFailedException;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that a KafkaSQL storage refusing to start (e.g. a compacted journal topic, or one with v2
 * records) stops startup and is never handed out half-initialized.
 */
class RegistryStorageProducerTest {

    private static final String REFUSAL = "The KafkaSQL journal topic 'kafkasql-journal' contains records from Apicurio Registry v2.";

    private RegistryStorageProducer producer;
    private KafkaSqlRegistryStorage kafkaSql;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaSql = mock(KafkaSqlRegistryStorage.class);
        Instance<KafkaSqlRegistryStorage> instance = mock(Instance.class);
        when(instance.get()).thenReturn(kafkaSql);

        producer = new RegistryStorageProducer();
        producer.log = LoggerFactory.getLogger(RegistryStorageProducerTest.class);
        producer.registryStorageType = "kafkasql";
        producer.kafkaSqlRegistryStorage = instance;
    }

    @Test
    void startupFailsWhenTheJournalTopicIsUnusable() {
        doThrow(new RuntimeAssertionFailedException(REFUSAL)).when(kafkaSql).initialize();

        var error = assertThrows(RuntimeAssertionFailedException.class, producer::initializeStorage);
        assertEquals("Runtime assertion failed: " + REFUSAL, error.getMessage());
    }

    @Test
    void storageWhoseInitializationFailedIsNotCached() {
        doThrow(new RuntimeAssertionFailedException(REFUSAL)).when(kafkaSql).initialize();

        // A bean that swallows the first failure (as a startup observer catching Exception would)
        // must not cause later callers to get the uninitialized storage.
        assertThrows(RuntimeAssertionFailedException.class, producer::raw);
        assertThrows(RuntimeAssertionFailedException.class, producer::raw);
        verify(kafkaSql, times(2)).initialize();
    }

    @Test
    void initializedStorageIsCached() {
        doNothing().when(kafkaSql).initialize();

        assertSame(kafkaSql, producer.raw());
        assertSame(kafkaSql, producer.raw());
        verify(kafkaSql, times(1)).initialize();
    }
}
