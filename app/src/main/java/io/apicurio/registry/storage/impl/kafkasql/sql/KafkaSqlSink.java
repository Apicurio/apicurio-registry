package io.apicurio.registry.storage.impl.kafkasql.sql;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessageKey;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.types.RegistryException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Logged
public class KafkaSqlSink {

    @Inject
    Logger log;

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    SqlRegistryStorage sqlStore;

    /**
     * Called by the {@link KafkaSqlRegistryStorage} main Kafka consumer loop to process a single
     * message in the topic.  Each message represents some attempt to modify the registry data.  So
     * each message much be consumed and applied to the in-memory SQL data store.
     * <p>
     * This method extracts the UUID from the message headers, delegates the message processing
     * to <code>doProcessMessage()</code>, and handles any exceptions that might occur. Finally
     * it will report the result to any local threads that may be waiting (via the coordinator).
     *
     * @param record
     */
    @ActivateRequestContext
    public void processMessage(ConsumerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record) {
        UUID requestId = extractUuid(record);
        log.debug("Processing Kafka message with UUID: {}", requestId);

        try {
            Object result = doProcessMessage(record);
            log.trace("Processed message key: {} value: {} result: {}", record.key().getMessageType(), record.value() != null ? record.value().toString() : "", result != null ? result.toString() : "");
            log.debug("Kafka message successfully processed. Notifying listeners of response.");
            coordinator.notifyResponse(requestId, result);
        } catch (RegistryException e) {
            log.debug("Registry exception detected: {}", e.getMessage());
            coordinator.notifyResponse(requestId, e);
        } catch (Throwable e) {
            log.debug("Unexpected exception detected: {}", e.getMessage());
            coordinator.notifyResponse(requestId, new RegistryException(e)); // TODO: Any exception (no wrapping)
        }
    }

    /**
     * Extracts the UUID from the message.  The UUID should be found in a message header.
     *
     * @param record
     */
    private UUID extractUuid(ConsumerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record) {
        return Optional.ofNullable(record.headers().headers("req"))
                .map(Iterable::iterator)
                .map(it -> {
                    return it.hasNext() ? it.next() : null;
                })
                .map(Header::value)
                .map(String::new)
                .map(UUID::fromString)
                .orElse(null);
    }

    /**
     * Process the message and return a result.  This method may also throw an exception if something
     * goes wrong.
     *
     * @param record
     */
    private Object doProcessMessage(ConsumerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record) {
        KafkaSqlMessage value = record.value();
        return value.dispatchTo(sqlStore);
    }
}
