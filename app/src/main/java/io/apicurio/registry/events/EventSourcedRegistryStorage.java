package io.apicurio.registry.events;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.events.dto.ArtifactId;
import io.apicurio.registry.events.dto.ArtifactRuleChange;
import io.apicurio.registry.events.dto.ArtifactStateChange;
import io.apicurio.registry.events.dto.RegistryEventType;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.decorator.RegistryStorageDecoratorBase;
import io.apicurio.registry.storage.decorator.RegistryStorageDecoratorOrderConstants;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.*;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EventSourcedRegistryStorage extends RegistryStorageDecoratorBase implements RegistryStorageDecorator {

    final Logger log;

    final EventsService eventsService;

    // Need to have an eager evaluation of the EventsService implementation
    EventSourcedRegistryStorage(EventsService eventService, Logger log) {
        this.log = log;
        this.eventsService = eventService;
    }

    private void fireEvent(RegistryEventType type, String artifactId, Object data, Throwable error) {
        if (error == null && data != null) {
            eventsService.triggerEvent(type, Optional.ofNullable(artifactId), data);
        }
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        if (!eventsService.isReady()) {
            throw new RuntimeException("Events Service not configured, please report this as a bug.");
        }
        log.info("Events service is configured: " + eventsService.isConfigured());
        return eventsService.isConfigured();
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#order()
     */
    @Override
    public int order() {
        return RegistryStorageDecoratorOrderConstants.EVENT_SOURCED_DECORATOR;
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) throws ArtifactNotFoundException, RegistryStorageException {
        delegate.updateArtifactState(groupId, artifactId, state);
        ArtifactStateChange data = new ArtifactStateChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setState(state.value());
        fireEvent(RegistryEventType.ARTIFACT_STATE_CHANGED, artifactId, data, null);
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.updateArtifactState(groupId, artifactId, version, state);
        ArtifactStateChange data = new ArtifactStateChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setState(state.value());
        data.setVersion(version);
        fireEvent(RegistryEventType.ARTIFACT_STATE_CHANGED, artifactId, data, null);
    }

    @Override
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId,
                                              String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        ArtifactMetaDataDto meta = delegate.createArtifact(groupId, artifactId, version, artifactType, content, references);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setVersion(meta.getVersion());
        data.setType(artifactType);
        fireEvent(RegistryEventType.ARTIFACT_CREATED, artifactId, data, null);
        return meta;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata (java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.delegate.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactAlreadyExistsException, RegistryStorageException {
        ArtifactMetaDataDto meta = delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setVersion(meta.getVersion());
        data.setType(artifactType);
        fireEvent(RegistryEventType.ARTIFACT_CREATED, artifactId, data, null);
        return meta;
    }

    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        List<String> set = delegate.deleteArtifact(groupId, artifactId);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        fireEvent(RegistryEventType.ARTIFACT_DELETED, artifactId, data, null);
        return set;
    }

    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        delegate.deleteArtifacts(groupId);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        fireEvent(RegistryEventType.ARTIFACTS_IN_GROUP_DELETED, groupId, data, null);
    }

    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {
        ArtifactMetaDataDto meta = delegate.updateArtifact(groupId, artifactId, version, artifactType, content, references);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setVersion(meta.getVersion());
        data.setType(artifactType);
        fireEvent(RegistryEventType.ARTIFACT_UPDATED, artifactId, data, null);
        return meta;
    }

    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version, String artifactType, ContentHandle content,
                                                          EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException {
        ArtifactMetaDataDto meta = delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setVersion(meta.getVersion());
        data.setType(artifactType);
        fireEvent(RegistryEventType.ARTIFACT_UPDATED, artifactId, data, null);
        return meta;
    }


    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        delegate.createArtifactRule(groupId, artifactId, rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setRule(rule.value());
        fireEvent(RegistryEventType.ARTIFACT_RULE_CREATED, artifactId, data, null);
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        delegate.deleteArtifactRules(groupId, artifactId);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        fireEvent(RegistryEventType.ALL_ARTIFACT_RULES_DELETED, artifactId, data, null);
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        delegate.updateArtifactRule(groupId, artifactId, rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setRule(rule.value());
        fireEvent(RegistryEventType.ARTIFACT_RULE_UPDATED, artifactId, data, null);
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        delegate.deleteArtifactRule(groupId, artifactId, rule);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setRule(rule.value());
        fireEvent(RegistryEventType.ARTIFACT_RULE_DELETED, artifactId, data, null);
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.deleteArtifactVersion(groupId, artifactId, version);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setVersion(version);
        fireEvent(RegistryEventType.ARTIFACT_DELETED, artifactId, data, null);
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        delegate.createGlobalRule(rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setRule(rule.value());
        fireEvent(RegistryEventType.GLOBAL_RULE_CREATED, null, data, null);
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        delegate.deleteGlobalRules();
        fireEvent(RegistryEventType.ALL_GLOBAL_RULES_DELETED, null, new HashMap<String, Object>(), null);
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        delegate.updateGlobalRule(rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setRule(rule.value());
        fireEvent(RegistryEventType.GLOBAL_RULE_UPDATED, null, data, null);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        delegate.deleteGlobalRule(rule);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setRule(rule.value());
        fireEvent(RegistryEventType.GLOBAL_RULE_DELETED, null, data, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        delegate.createGroup(group);
        ArtifactId data = new ArtifactId();
        data.setGroupId(group.getGroupId());
        fireEvent(RegistryEventType.GROUP_CREATED, group.getGroupId(), data, null);
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecoratorBase#updateGroupMetaData(java.lang.String, io.apicurio.registry.storage.dto.EditableGroupMetaDataDto)
     */
    @Override
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) throws GroupNotFoundException, RegistryStorageException {
        delegate.updateGroupMetaData(groupId, dto);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        fireEvent(RegistryEventType.GROUP_UPDATED, groupId, data, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        delegate.deleteGroup(groupId);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        fireEvent(RegistryEventType.GROUP_DELETED, groupId, data, null);
    }
}
