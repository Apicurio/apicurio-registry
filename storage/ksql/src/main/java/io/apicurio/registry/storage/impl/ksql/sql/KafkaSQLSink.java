package io.apicurio.registry.storage.impl.ksql.sql;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.ksql.KafkaSqlCoordinator;
import io.apicurio.registry.storage.impl.ksql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.GlobalIdGenerator;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.storage.proto.Str.ActionType;
import io.apicurio.registry.storage.proto.Str.ArtifactValue;
import io.apicurio.registry.storage.proto.Str.MetaDataValue;
import io.apicurio.registry.storage.proto.Str.RuleValue;
import io.apicurio.registry.storage.proto.Str.StorageValue.ValueCase;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.RuleType;

//ArtifactValue artifact = 5;
//MetaDataValue metadata = 6;
//RuleValue rule = 7;
//SnapshotValue snapshot = 8;
//ArtifactState state = 9;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class KafkaSQLSink {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    @Named("SQLRegistryStorage")
    SQLRegistryStorage sqlStorage;

    public void processStorageAction(ConsumerRecord<String, Str.StorageValue> record) {
        UUID requestId = Optional.ofNullable(record.headers().headers("req"))
                .map(Iterable::iterator)
                .map(it -> {
                    return it.hasNext() ? it.next() : null;
                })
                .map(Header::value)
                .map(String::new)
                .map(UUID::fromString)
                .orElse(null);

        try {
            Object result = internalProcessStorageAction(record);
            coordinator.notifyResponse(requestId, result);
        } catch (RegistryException e ) {
            coordinator.notifyResponse(requestId, e);
        } catch (RuntimeException e ) {
            coordinator.notifyResponse(requestId, new RegistryException(e));
        }
    }

    public Object internalProcessStorageAction(ConsumerRecord<String, Str.StorageValue> record) {
        ValueCase valueCase = record.value().getValueCase();
        switch (valueCase) {
            case ARTIFACT:
                return handleArtifact(record);
            case METADATA:
                return handleMetadata(record);
            case RULE:
                return handleRule(record);
            case STATE:
                return handleArtifactState(record);
            default :
                log.warn("Unrecognized value key: %s", record.key());
                return null;
        }
    }

    private Object handleArtifact(ConsumerRecord<String, Str.StorageValue> record) {
        int partition = record.partition();
        long offset = record.offset();
        Long globalId = toGlobalId(offset, partition);
        GlobalIdGenerator globalIdGenerator = () -> {
            return globalId;
        };

        String artifactId = record.key();
        Str.StorageValue storageAction = record.value();

        ArtifactValue artifactValue = storageAction.getArtifact();
        ArtifactType artifactType = ArtifactType.values()[artifactValue.getArtifactType()];

        ActionType actionType = storageAction.getType();
        switch (actionType) {
            case CREATE:
                return sqlStorage.createArtifact(artifactId, artifactType, ContentHandle.create(artifactValue.getContent().toByteArray()), globalIdGenerator);
            case UPDATE:
                return sqlStorage.updateArtifact(artifactId, artifactType, ContentHandle.create(artifactValue.getContent().toByteArray()), globalIdGenerator);
            case DELETE:
                if (storageAction.getVersion() == -1L) {
                    return sqlStorage.deleteArtifact(artifactId);
                } else {
                    sqlStorage.deleteArtifactVersion(artifactId, storageAction.getVersion());
                    break;
                }
            case READ:
                log.warn("Read storage action, nothing to do. artifact: %s", artifactId);
                break;
            case UNDEFINED:
            case UNRECOGNIZED:
                log.warn("Unrecognized storage action type for artifact: %s", artifactId);
                break;
        }
        return null;
    }

    private Object handleMetadata(ConsumerRecord<String, Str.StorageValue> record) {
        String artifactId = record.key();
        Str.StorageValue storageAction = record.value();

        MetaDataValue metadata = storageAction.getMetadata();

        long artifactVersion = storageAction.getVersion();

        ActionType actionType = storageAction.getType();
        switch (actionType) {
            case CREATE:
                log.warn("Create metadata storage action, this action does not exist. nothing to do. artifact: %s", artifactId);
                break;
            case UPDATE:
                List<String> labels = Optional.ofNullable(metadata.getLabels())
                    .map(l -> l.split(","))
                    .map(l -> Stream.of(l))
                    .orElseGet(Stream::empty)
                    .collect(Collectors.toList());
                EditableArtifactMetaDataDto editablemeta = new EditableArtifactMetaDataDto(metadata.getName(), metadata.getDescription(), labels, metadata.getPropertiesMap());
                if (artifactVersion == -1) {
                    sqlStorage.updateArtifactMetaData(artifactId, editablemeta);
                } else {
                    sqlStorage.updateArtifactVersionMetaData(artifactId, artifactVersion, editablemeta);
                }
                break;
            case DELETE:
                if (artifactVersion == -1) {
                    log.warn("Delete artifact metadata storage action, this action does not exist. nothing to do. artifact: %s", artifactId);
                } else {
                    sqlStorage.deleteArtifactVersionMetaData(artifactId, artifactVersion);
                }
                break;
            case READ:
                log.warn("Read storage action, nothing to do. artifact: %s", artifactId);
                break;
            case UNDEFINED:
            case UNRECOGNIZED:
                log.warn("Unrecognized storage action artifact: %s", artifactId);
                break;
        }
        return null;
    }

    private Object handleArtifactState(ConsumerRecord<String, Str.StorageValue> record) {
        String artifactId = record.key();
        Str.StorageValue storageAction = record.value();

        Str.ArtifactState state = storageAction.getState();
        ArtifactState newState = ArtifactState.valueOf(state.name());

        long version = storageAction.getVersion();

        ActionType actionType = storageAction.getType();
        switch (actionType) {
            case CREATE:
                log.warn("Create artifact state storage action, this action does not exist. nothing to do. artifact: %s", artifactId);
                break;
            case UPDATE:
                sqlStorage.updateArtifactState(artifactId, newState, Long.valueOf(version).intValue());
                break;
            case DELETE:
                log.warn("Delete artifact state storage action, this action does not exist. nothing to do. artifact: %s", artifactId);
                break;
            case READ:
                log.warn("Read storage action, nothing to do. artifact: %s", artifactId);
                break;
            case UNDEFINED:
            case UNRECOGNIZED:
                log.warn("Unrecognized storage action artifact: %s", artifactId);
                break;
        }
        return null;
    }

    private Object handleRule(ConsumerRecord<String, Str.StorageValue> record) {
        String artifactId = record.key();
        Str.StorageValue storageAction = record.value();

        RuleValue ruleValue = storageAction.getRule();

        Str.RuleType rtv = ruleValue.getType();
        RuleType rule = (rtv != null && rtv != Str.RuleType.__NONE) ? RuleType.valueOf(rtv.name()) : null;

        RuleConfigurationDto config = null;
        if (ruleValue.getConfiguration() != null) {
            config = new RuleConfigurationDto(ruleValue.getConfiguration());
        }

        ActionType actionType = storageAction.getType();
        switch (actionType) {
            case CREATE:
                if (isGlobalRules(artifactId)) {
                    sqlStorage.createGlobalRule(rule, config);
                } else {
                    return sqlStorage.createArtifactRuleAsync(artifactId, rule, config);
                }
                break;
            case UPDATE:
                if (isGlobalRules(artifactId)) {
                    sqlStorage.updateGlobalRule(rule, config);
                } else {
                    sqlStorage.updateArtifactRule(artifactId, rule, config);
                }
                break;
            case DELETE:
                if (isGlobalRules(artifactId)) {
                    if (rule == null) {
                        sqlStorage.deleteGlobalRules();
                    } else {
                        sqlStorage.deleteGlobalRule(rule);
                    }
                } else {
                    if (rule == null) {
                        sqlStorage.deleteArtifactRules(artifactId);
                    } else {
                        sqlStorage.deleteArtifactRule(artifactId, rule);
                    }
                }
                break;
            case READ:
                log.warn("Read storage action, nothing to do. artifact: %s", artifactId);
                break;
            case UNDEFINED:
            case UNRECOGNIZED:
                log.warn("Unrecognized storage action artifact: %s", artifactId);
                break;
        }
        return null;
    }

    private boolean isGlobalRules(String artifactId) {
        return artifactId.equals(KafkaSqlRegistryStorage.GLOBAL_RULES_ID);
    }

    public long toGlobalId(long offset, int partition) {
        return getBaseOffset() + (offset << 16) + partition;
    }

    // just to make sure we can always move the whole system
    // and not get duplicates; e.g. after move baseOffset = max(globalId) + 1
    public long getBaseOffset() {
        //TODO
        //        return Long.parseLong(properties.getProperty("storage.base.offset", "0"));
        return 0;
    }

}
