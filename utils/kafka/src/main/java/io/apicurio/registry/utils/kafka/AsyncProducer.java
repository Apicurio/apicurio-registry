/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.utils.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * An async wrapper for kafka producer that is resilient in the event of failures - it recreates the underlying
 * kafka producer when unrecoverable error occurs.
 * This producer is not suitable for transactional use. It is suitable for normal or idempotent use.
 */
public class AsyncProducer<K, V> implements ProducerActions<K, V> {
    private static final Logger log = LoggerFactory.getLogger(AsyncProducer.class);

    private final Properties producerProps;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valSerializer;

    public AsyncProducer(Properties producerProps, Serializer<K> keySerializer, Serializer<V> valSerializer) {
        this.producerProps = Objects.requireNonNull(producerProps, "producerProps");
        this.keySerializer = Objects.requireNonNull(keySerializer, "keySerializer");
        this.valSerializer = Objects.requireNonNull(valSerializer, "valSerializer");
    }

    private KafkaProducer<K, V> producer;
    private boolean closed;

    @Override
    public CompletableFuture<RecordMetadata> apply(ProducerRecord<K, V> record) {
        CompletableFuture<RecordMetadata> result = null;
        try {
            KafkaProducer<K, V> producer = getProducer();
            result = new CFC(producer);
            producer.send(record, (CFC) result);
        } catch (Exception e) {
            if (result != null) {
                ((CFC) result).onCompletion(null, e);
            } else {
                result = new CompletableFuture<>();
                result.completeExceptionally(e);
            }
        }
        return result;
    }

    @Override
    public void close() {
        closeProducer(null, false);
    }

    private synchronized KafkaProducer<K, V> getProducer() {
        if (producer == null) {
            if (closed) {
                throw new IllegalStateException("This producer is already closed.");
            }
            log.info("Creating new resilient producer.");
            producer = new KafkaProducer<>(producerProps, keySerializer, valSerializer);
        }
        return producer;
    }

    private synchronized void closeProducer(KafkaProducer<?, ?> producer, boolean fromCallback) {
        try {
            if (producer == null) producer = this.producer;
            if (producer != null && producer == this.producer) {
                try {
                    log.info("Closing resilient producer.");
                    if (fromCallback) {
                        producer.close(Duration.ZERO);
                    } else {
                        producer.close();
                    }
                } catch (Exception e) {
                    log.warn("Exception caught while closing producer.", e);
                } finally {
                    this.producer = null;
                }
            }
        } finally {
            if (!fromCallback) closed = true;
        }
    }

    private class CFC extends CompletableFuture<RecordMetadata> implements Callback {
        private final KafkaProducer<?, ?> producer;

        CFC(KafkaProducer<?, ?> producer) {
            this.producer = producer;
        }

        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            if (exception != null) {
                try {
                    if (isFatalException(exception)) {
                        closeProducer(producer, true);
                    }
                } finally {
                    completeExceptionally(exception);
                }
            } else {
                complete(metadata);
            }
        }

        private boolean isFatalException(Exception e) {
            return e instanceof UnsupportedVersionException ||
                   e instanceof AuthorizationException ||
                   e instanceof ProducerFencedException ||
                   e instanceof OutOfOrderSequenceException;
        }
    }
}
