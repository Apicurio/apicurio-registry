package io.apicurio.registry.utils.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface ProducerActions<K, V>
        extends Function<ProducerRecord<K, V>, CompletableFuture<RecordMetadata>>, AutoCloseable {
}
