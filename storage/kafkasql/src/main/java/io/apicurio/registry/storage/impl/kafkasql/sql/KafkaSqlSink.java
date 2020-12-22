package io.apicurio.registry.storage.impl.kafkasql.sql;

import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlConfiguration;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactRuleKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactVersionKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ContentKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.GlobalRuleKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageType;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactRuleValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactVersionValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ContentValue;
import io.apicurio.registry.storage.impl.kafkasql.values.GlobalRuleValue;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import io.apicurio.registry.storage.impl.sql.GlobalIdGenerator;
import io.apicurio.registry.types.RegistryException;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
@Logged
public class KafkaSqlSink {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    KafkaSqlStore sqlStore;

    @Inject
    KafkaSqlConfiguration configuration;
    
    @Inject
    KafkaSqlSubmitter submitter;

    /**
     * Called by the {@link KafkaSqlRegistryStorage} main Kafka consumer loop to process a single
     * message in the topic.  Each message represents some attempt to modify the registry data.  So
     * each message much be consumed and applied to the in-memory SQL data store.
     * 
     * This method extracts the UUID from the message headers, delegates the message processing
     * to <code>doProcessMessage()</code>, and handles any exceptions that might occur. Finally
     * it will report the result to any local threads that may be waiting (via the coordinator).
     * 
     * @param record
     */
    public void processMessage(ConsumerRecord<MessageKey, MessageValue> record) {
        // If the key is null, we couldn't deserialize the message
        if (record.key() == null) {
            log.info("Discarded an unreadable/unrecognized message.");
            return;
        }

        // If the value is null, then this is a tombstone (or unrecognized) message and should not 
        // be processed.
        if (record.value() == null) {
            log.info("Discarded a (presumed) tombstone message with key: {}", record.key());
            return;
        }
        
        UUID requestId = extractUuid(record);
        log.debug("Processing Kafka message with UUID: {}", requestId.toString());

        try {
            Object result = doProcessMessage(record);
            log.debug("Kafka message successfully processed. Notifying listeners of response.");
            coordinator.notifyResponse(requestId, result);
        } catch (RegistryException e) {
            log.debug("Registry exception detected: {}", e.getMessage());
            coordinator.notifyResponse(requestId, e);
        } catch (Throwable e) {
            log.debug("Unexpected exception detected: {}", e.getMessage());
            coordinator.notifyResponse(requestId, new RegistryException(e));
        }
    }

    /**
     * Extracts the UUID from the message.  The UUID should be found in a message header.
     * @param record
     */
    private UUID extractUuid(ConsumerRecord<MessageKey, MessageValue> record) {
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
     * @param record
     */
    private Object doProcessMessage(ConsumerRecord<MessageKey, MessageValue> record) {
        MessageKey key = record.key();
        MessageValue value = record.value();

        GlobalIdGenerator globalIdGenerator = () -> {
            int partition = record.partition();
            long offset = record.offset();
            Long globalId = toGlobalId(offset, partition);
            return globalId;
        };

        MessageType messageType = key.getType();
        switch (messageType) {
            case Artifact:
                return processArtifactMessage((ArtifactKey) key, (ArtifactValue) value, globalIdGenerator);
            case ArtifactRule:
                return processArtifactRuleMessage((ArtifactRuleKey) key, (ArtifactRuleValue) value);
            case ArtifactVersion:
                return processArtifactVersion((ArtifactVersionKey) key, (ArtifactVersionValue) value);
            case Content:
                return processContent((ContentKey) key, (ContentValue) value);
            case GlobalRule:
                return processGlobalRuleVersion((GlobalRuleKey) key, (GlobalRuleValue) value);
            default:
                log.warn("Unrecognized message type: %s", record.key());
                throw new RegistryStorageException("Unexpected message type: " + messageType.name());
        }
    }

    /**
     * Process a Kafka message of type "artifact".  This includes creating, updating, and deleting artifacts.
     * @param key
     * @param value
     * @param globalIdGenerator 
     */
    private Object processArtifactMessage(ArtifactKey key, ArtifactValue value, GlobalIdGenerator globalIdGenerator) throws RegistryStorageException {
        try {
            switch (value.getAction()) {
                case Create:
                    return sqlStore.createArtifactWithMetadata(key.getArtifactId(), value.getArtifactType(),
                            value.getContentHash(), value.getCreatedBy(), value.getCreatedOn(),
                            value.getMetaData(), globalIdGenerator);
                case Update:
                    return sqlStore.updateArtifactWithMetadata(key.getArtifactId(), value.getArtifactType(),
                            value.getContentHash(), value.getCreatedBy(), value.getCreatedOn(),
                            value.getMetaData(), globalIdGenerator);
                case Delete:
                    return sqlStore.deleteArtifact(key.getArtifactId());
                case Clear:
                default:
                    log.warn("Unsupported artifact message action: %s", key.getType().name());
                    throw new RegistryStorageException("Unsupported artifact message action: " + value.getAction());
            }
        } catch (ArtifactNotFoundException | ArtifactAlreadyExistsException e) {
            // Send a tombstone message to clean up the unique Kafka message that caused this failure.  We may be
            // able to do this for other errors, but these two are definitely safe.
            submitter.send(key, null);
            throw e;
        }
    }

    /**
     * Process a Kafka message of type "artifact rule".  This includes creating, updating, and deleting
     * rules for a specific artifact.
     * @param key
     * @param value
     */
    private Object processArtifactRuleMessage(ArtifactRuleKey key, ArtifactRuleValue value) {
        switch (value.getAction()) {
            case Create:
                // Note: createArtifactRuleAsync() must be called instead of createArtifactRule() because that's what 
                // KafkaSqlRegistryStorage::createArtifactRuleAsync() expects (a return value)
                return sqlStore.createArtifactRuleAsync(key.getArtifactId(), key.getRuleType(), value.getConfig());
            case Update:
                sqlStore.updateArtifactRule(key.getArtifactId(), key.getRuleType(), value.getConfig());
                return null;
            case Delete:
                sqlStore.deleteArtifactRule(key.getArtifactId(), key.getRuleType());
                return null;
            case Clear:
            default:
                log.warn("Unsupported artifact rule message action: %s", key.getType().name());
                throw new RegistryStorageException("Unsupported artifact-rule message action: " + value.getAction());
        }
    }

    /**
     * Process a Kafka message of type "artifact version".  This includes:
     * 
     *  - Updating version meta-data and state
     *  - Deleting version meta-data and state
     *  - Deleting an artifact version
     * 
     * @param key
     * @param value
     */
    private Object processArtifactVersion(ArtifactVersionKey key, ArtifactVersionValue value) {
        switch (value.getAction()) {
            case Update:
                sqlStore.updateArtifactVersionMetaDataAndState(key.getArtifactId(), key.getVersion(), value.getMetaData(), value.getState());
                return null;
            case Delete:
                sqlStore.deleteArtifactVersion(key.getArtifactId(), key.getVersion());
                return null;
            case Clear:
                sqlStore.deleteArtifactVersionMetaData(key.getArtifactId(), key.getVersion());
                return null;
            case Create:
            default:
                log.warn("Unsupported artifact version message action: %s", key.getType().name());
                throw new RegistryStorageException("Unsupported artifact-version message action: " + value.getAction());
        }
    }

    /**
     * Process a Kafka message of type "content".  This primarily means creating or updating a row in
     * the content table.
     * @param key
     * @param value
     */
    private Object processContent(ContentKey key, ContentValue value) {
        switch (value.getAction()) {
            case Create:
                if (!sqlStore.isContentExists(key.getContentHash())) {
                    sqlStore.storeContent(key.getContentHash(), value.getArtifactType(), value.getContent());
                }
                break;
            case Delete:
            case Update:
            case Clear:
            default:
                log.warn("Unsupported content message action: %s", key.getType().name());
                throw new RegistryStorageException("Unsupported content message action: " + value.getAction());
        }
        return null;
    }

    /**
     * Process a Kafka message of type "global rule".  This includes creating, updating, and deleting
     * global rules.
     * 
     * @param key
     * @param value
     */
    private Object processGlobalRuleVersion(GlobalRuleKey key, GlobalRuleValue value) {
        switch (value.getAction()) {
            case Create:
                sqlStore.createGlobalRule(key.getRuleType(), value.getConfig());
                return null;
            case Update:
                sqlStore.updateGlobalRule(key.getRuleType(), value.getConfig());
                return null;
            case Delete:
                sqlStore.deleteGlobalRule(key.getRuleType());
                return null;
            case Clear:
            default:
                log.warn("Unsupported global rule message action: %s", key.getType().name());
                throw new RegistryStorageException("Unsupported global-rule message action: " + value.getAction());
        }
    }

    public long toGlobalId(long offset, int partition) {
        return getBaseOffset() + (offset << 16) + partition;
    }

    // just to make sure we can always move the whole system
    // and not get duplicates; e.g. after move baseOffset = max(globalId) + 1
    public long getBaseOffset() {
        return configuration.baseOffset();
    }

}
