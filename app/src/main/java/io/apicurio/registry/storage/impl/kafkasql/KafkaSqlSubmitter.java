package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.storage.impl.util.ProducerActions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Logged
public class KafkaSqlSubmitter {

    public static final String REQUEST_ID_HEADER = "req";
    public static final String MESSAGE_TYPE_HEADER = "mt";
    public static final String BOOTSTRAP_MESSAGE_TYPE = "Bootstrap";

    @Inject
    KafkaSqlConfiguration configuration;

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    @Named("KafkaSqlJournalProducer")
    ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage> producer;

    /**
     * Constructor.
     */
    public KafkaSqlSubmitter() {
    }

    // Once the application is done, close the producer.
    public void handleShutdown(@Observes Shutdown shutdownEvent) throws Exception {
        producer.close();
    }

    /**
     * Sends a message to the Kafka topic.
     * 
     * @param key
     * @param value
     */
    private CompletableFuture<UUID> send(KafkaSqlMessageKey key, KafkaSqlMessage value) {
        UUID requestId = coordinator.createUUID();
        RecordHeader requestIdHeader = new RecordHeader(REQUEST_ID_HEADER,
                requestId.toString().getBytes(StandardCharsets.UTF_8));
        RecordHeader messageTypeHeader = new RecordHeader(MESSAGE_TYPE_HEADER,
                key.getMessageType().getBytes(StandardCharsets.UTF_8));
        ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record = new ProducerRecord<>(
                configuration.getTopic(), null, key, value, List.of(requestIdHeader, messageTypeHeader));
        return producer.apply(record).thenApply(rm -> requestId);
    }

    public void submitBootstrap(String bootstrapId) {
        KafkaSqlMessageKey key = KafkaSqlMessageKey.builder().messageType(BOOTSTRAP_MESSAGE_TYPE).uuid(bootstrapId)
                .build();
        send(key, null);
    }

    public CompletableFuture<UUID> submitMessage(KafkaSqlMessage message) {
        var key = message.getKey();
        return send(key, message);
    }

}
