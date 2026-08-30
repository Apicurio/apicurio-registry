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
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.apicurio.registry.utils.ConcurrentUtil.blockOnResult;

@ApplicationScoped
@Logged
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlSubmitter {

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

    private CompletableFuture<UUID> send(KafkaSqlMessageKey key, KafkaSqlMessage value, boolean tracked) {
        UUID requestId = tracked ? coordinator.get().createUUID() : UUID.randomUUID();
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
        blockOnResult(send(key, null, false));
    }

    public CompletableFuture<UUID> submitMessage(KafkaSqlMessage message) {
        var key = message.getKey();
        return send(key, message, true);
    }

    public void submitFireAndForget(KafkaSqlMessage message) {
        var key = message.getKey();
        send(key, message, false);
    }

}
