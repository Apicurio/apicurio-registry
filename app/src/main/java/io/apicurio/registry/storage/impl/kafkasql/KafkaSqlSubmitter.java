package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.common.apps.config.Info;
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
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import static io.apicurio.registry.utils.ConcurrentUtil.blockOnResult;

@ApplicationScoped
@Logged
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlSubmitter {

    // Static rather than injected like the other kafkasql beans' loggers: KafkaSqlSubmitterTest
    // builds this class with new, where an injected logger would be null.
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

    /**
     * Submits a message whose result a caller will wait for; pass the returned UUID to
     * KafkaSqlCoordinator.waitForResponse (see createUUID). Messages nobody waits for go
     * through submitFireAndForget instead.
     */
    public CompletableFuture<UUID> submitMessage(KafkaSqlMessage message) {
        var key = message.getKey();
        // Resolved once: a second lookup throwing on a cleanup path would lose the forget and
        // replace the send failure the caller is waiting on with the lookup failure.
        KafkaSqlCoordinator kafkaSqlCoordinator = coordinator.get();
        UUID requestId = kafkaSqlCoordinator.createUUID();
        CompletableFuture<RecordMetadata> produced;
        try {
            produced = send(key, message, requestId);
        } catch (Throwable e) {
            // The record never reached the broker, so no response will ever arrive for
            // this UUID; the entry registered above must not linger in the coordinator.
            kafkaSqlCoordinator.forget(requestId);
            throw e;
        }
        return produced
                .thenApply(rm -> requestId)
                .whenComplete((uuid, error) -> {
                    if (error != null) {
                        // Forgets even if the record did reach the log. A response arriving
                        // later then finds no entry, and notifyResponse drops it.
                        kafkaSqlCoordinator.forget(requestId);
                    }
                });
    }

    /**
     * Submits a message that no caller will ever wait for (usage events, old-usage
     * cleanup) without registering it in the coordinator: nothing would ever wait on
     * the entry and nothing would notify it, so registering one here would leak it.
     * Best effort only: a failure thrown before the record reaches the producer (the
     * lookups, the record construction) still propagates to the caller, as it always has.
     * A Kafka client failure, synchronous or not, arrives in whenComplete as a failed future
     * (AsyncProducer.apply converts a throw from send into one), where it is logged and the
     * message dropped.
     */
    public void submitFireAndForget(KafkaSqlMessage message) {
        var key = message.getKey();
        String messageType = key.getMessageType();
        send(key, message, UUID.randomUUID()).whenComplete((rm, error) -> {
            if (error != null) {
                // One line per drop at WARN, the stack trace only at DEBUG: during a broker
                // outage a single usage-telemetry flush can drop up to 2000 messages, and a
                // trace for each would bury everything else in the log.
                log.warn("Dropped fire-and-forget message of type {} after a failed send: {}",
                        messageType, error.toString());
                log.debug("Failed fire-and-forget send of type {}", messageType, error);
            }
        });
    }

}
