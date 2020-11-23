package io.apicurio.registry.storage.impl.ksql.sql;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.ksql.KafkaSqlCoordinator;
import io.apicurio.registry.storage.impl.ksql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.storage.proto.Str.ActionType;
import io.apicurio.registry.storage.proto.Str.ArtifactValue;
import io.apicurio.registry.storage.proto.Str.MetaDataValue;
import io.apicurio.registry.storage.proto.Str.RuleValue;
import io.apicurio.registry.storage.proto.Str.StorageValue;
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

    public void processStorageAction(UUID requestId, String artifactId, Str.StorageValue storageAction) {
        try {
            Object result = internalProcessStorageAction(artifactId, storageAction);
            coordinator.notifyResponse(requestId, result);
        } catch (RegistryException e ) {
            coordinator.notifyResponse(requestId, e);
        } catch (RuntimeException e ) {
            coordinator.notifyResponse(requestId, new RegistryException(e));
        }
    }

    public Object internalProcessStorageAction(String artifactId, Str.StorageValue storageAction) {
        ValueCase valueCase = storageAction.getValueCase();
        switch (valueCase) {
            case ARTIFACT:
                return handleArtifact(artifactId, storageAction);
            case METADATA:
                return handleMetadata(artifactId, storageAction);
            case RULE:
                return handleRule(artifactId, storageAction);
            case STATE:
                return handleArtifactState(artifactId, storageAction);
            default :
                log.warn("Unrecognized value artifact: %s", artifactId);
                return null;
        }
    }

    private Object handleArtifact(String artifactId, StorageValue storageAction) {
        ArtifactValue artifactValue = storageAction.getArtifact();

        ArtifactType artifactType = ArtifactType.values()[artifactValue.getArtifactType()];


        ActionType actionType = storageAction.getType();
        switch (actionType) {
            case CREATE:
                return sqlStorage.createArtifact(artifactId, artifactType, ContentHandle.create(artifactValue.getContent().toByteArray()));
            case UPDATE:
                return sqlStorage.updateArtifact(artifactId, artifactType, ContentHandle.create(artifactValue.getContent().toByteArray()));
            case DELETE:
                return sqlStorage.deleteArtifact(artifactId);
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

    private Object handleMetadata(String artifactId, StorageValue storageAction) {
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

    private Object handleArtifactState(String artifactId, StorageValue storageAction) {
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

    private Object handleRule(String artifactId, StorageValue storageAction) {
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

}
