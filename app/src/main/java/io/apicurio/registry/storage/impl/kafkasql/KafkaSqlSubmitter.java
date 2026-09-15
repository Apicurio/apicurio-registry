package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.storage.impl.util.ProducerActions;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.apicurio.registry.utils.ConcurrentUtil.blockOnResult;

@ApplicationScoped
@Logged
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlSubmitter {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlSubmitter.class);

    public static final String REQUEST_ID_HEADER = "req";
    public static final String MESSAGE_TYPE_HEADER = "mt";
    public static final String BOOTSTRAP_MESSAGE_TYPE = "Bootstrap";

    @ConfigProperty(name = "apicurio.storage.kind", defaultValue = "sql")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, gitops, or kubernetesops", availableSince = "3.0.0")
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
     * Sends a message to the Kafka topic. The caller supplies the request ID: tracked
     * sends get one from the coordinator, untracked sends a random UUID that keeps the
     * header shape journal-record classification relies on.
     */
    private CompletableFuture<RecordMetadata> send(KafkaSqlMessageKey key, KafkaSqlMessage value, UUID requestId) {
        RecordHeader requestIdHeader = new RecordHeader(REQUEST_ID_HEADER,
                requestId.toString().getBytes(StandardCharsets.UTF_8));
        RecordHeader messageTypeHeader = new RecordHeader(MESSAGE_TYPE_HEADER,
                key.getMessageType().getBytes(StandardCharsets.UTF_8));
        ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record = new ProducerRecord<>(
                configuration.get().getTopic(), null, key, value, List.of(requestIdHeader, messageTypeHeader));
        return producer.get().apply(record);
    }

    /**
     * Submits a bootstrap marker message and blocks until it is durably written to Kafka.
     *
     * @param bootstrapId unique identifier for this bootstrap sequence
     */
    public void submitBootstrap(String bootstrapId) {
        KafkaSqlMessageKey key = KafkaSqlMessageKey.builder().messageType(BOOTSTRAP_MESSAGE_TYPE).uuid(bootstrapId)
                .build();
        blockOnResult(send(key, null, UUID.randomUUID()));
    }

    public CompletableFuture<UUID> submitMessage(KafkaSqlMessage message) {
        var key = message.getKey();
        UUID requestId = coordinator.get().createUUID();
        CompletableFuture<RecordMetadata> produced;
        try {
            produced = send(key, message, requestId);
        } catch (RuntimeException e) {
            // The record never reached the broker, so no response will ever arrive for
            // this UUID; the entry registered above must not linger in the coordinator.
            coordinator.get().forget(requestId);
            throw e;
        }
        return produced
                .thenApply(rm -> requestId)
                .whenComplete((uuid, error) -> {
                    if (error != null) {
                        // Same reason as above, for send failures that surface asynchronously.
                        coordinator.get().forget(requestId);
                    }
                });
    }

    /**
     * Submits a message that no caller will ever wait for (usage events, old-usage
     * cleanup) without registering it in the coordinator: waitForResponse is the only
     * thing that removes a registered entry, so registering one here would leak it.
     * Best effort only: a failure surfacing asynchronously is logged and the message
     * dropped, while a synchronous send failure still propagates to the caller, as it
     * always has.
     */
    public void submitFireAndForget(KafkaSqlMessage message) {
        var key = message.getKey();
        send(key, message, UUID.randomUUID()).whenComplete((rm, error) -> {
            if (error != null) {
                log.warn("Dropped fire-and-forget message of type {} after a failed send.",
                        key.getMessageType(), error);
            }
        });
    }

}
