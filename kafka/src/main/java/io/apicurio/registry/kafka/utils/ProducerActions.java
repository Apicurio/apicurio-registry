package io.apicurio.registry.kafka.utils;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * @author Ales Justin
 */
public interface ProducerActions<K, V> extends Function<ProducerRecord<K, V>, CompletableFuture<RecordMetadata>>, AutoCloseable {
}
