package io.apicurio.registry.storage.impl.kafkasql;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KafkaSqlShutdownTest {

    @SuppressWarnings("unchecked")
    @Test
    void onDestroyCallsWakeupNotClose() throws Exception {
        KafkaSqlRegistryStorage storage = new KafkaSqlRegistryStorage();
        storage.log = mock(Logger.class);

        KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> journalConsumer = mock(KafkaConsumer.class);
        KafkaConsumer<String, String> snapshotsConsumer = mock(KafkaConsumer.class);

        storage.journalConsumer = journalConsumer;
        storage.snapshotsConsumer = snapshotsConsumer;

        setPrivateField(storage, "stopped", false);

        storage.onDestroy();

        verify(journalConsumer).wakeup();
        verify(journalConsumer, never()).close();

        // The snapshots consumer is closed directly (it is not used from another thread)
        verify(snapshotsConsumer).close();

        boolean stopped = (boolean) getPrivateField(storage, "stopped");
        assertTrue(stopped, "stopped flag should be true after onDestroy()");
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
