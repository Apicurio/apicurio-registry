package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.storage.impl.util.ProducerActions;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Logged
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlSubmitter {

    public static final String REQUEST_ID_HEADER = "req";
    public static final String MESSAGE_TYPE_HEADER = "mt";
    public static final String BOOTSTRAP_MESSAGE_TYPE = "Bootstrap";

    @ConfigProperty(name = "apicurio.storage.kind", defaultValue = "sql")
    String storageType;

    @Inject
    Instance<KafkaSqlConfiguration> configuration;

    @Inject
    Instance<KafkaSqlCoordinator> coordinator;

    @Inject
    @Named("KafkaSqlJournalProducer")
    Instance<ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage>> producer;

    private boolean isKafkaSqlStorage() {
        return "kafkasql".equals(storageType);
    }

    /**
     * Constructor.
     */
    public KafkaSqlSubmitter() {
    }

    // Once the application is done, close the producer.
    public void handleShutdown(@Observes Shutdown shutdownEvent) throws Exception {
        if (isKafkaSqlStorage() && producer.isResolvable()) {
            producer.get().close();
        }
    }

    /**
     * Sends a message to the Kafka topic.
     *
     * @param key
     * @param value
     */
    private CompletableFuture<UUID> send(KafkaSqlMessageKey key, KafkaSqlMessage value) {
        UUID requestId = coordinator.get().createUUID();
        RecordHeader requestIdHeader = new RecordHeader(REQUEST_ID_HEADER,
                requestId.toString().getBytes(StandardCharsets.UTF_8));
        RecordHeader messageTypeHeader = new RecordHeader(MESSAGE_TYPE_HEADER,
                key.getMessageType().getBytes(StandardCharsets.UTF_8));
        ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record = new ProducerRecord<>(
                configuration.get().getTopic(), null, key, value, List.of(requestIdHeader, messageTypeHeader));
        return producer.get().apply(record).thenApply(rm -> requestId);
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
