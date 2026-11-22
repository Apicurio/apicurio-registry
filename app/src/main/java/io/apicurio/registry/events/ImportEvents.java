package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.ContractRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;

/**
 * Builds the events emitted when entities are imported. Imports reuse the event types of the
 * equivalent API operations, so consumers see imported data the same way as created data.
 */
public final class ImportEvents {

    private ImportEvents() {
    }

    public static OutboxEvent of(GroupEntity entity) {
        return GroupCreated.of(GroupMetaDataDto.builder().groupId(entity.groupId)
                .description(entity.description).createdOn(entity.createdOn).build());
    }

    public static OutboxEvent of(ArtifactEntity entity) {
        return ArtifactCreated.of(ArtifactMetaDataDto.builder().groupId(entity.groupId)
                .artifactId(entity.artifactId).name(entity.name).description(entity.description)
                .createdOn(entity.createdOn).build());
    }

    public static OutboxEvent of(ArtifactVersionEntity entity) {
        return ArtifactVersionCreated.of(ArtifactVersionMetaDataDto.builder().groupId(entity.groupId)
                .artifactId(entity.artifactId).version(entity.version).name(entity.name)
                .description(entity.description).build());
    }

    public static OutboxEvent of(GlobalRuleEntity entity) {
        return GlobalRuleConfigured.of(entity.ruleType, new RuleConfigurationDto(entity.configuration));
    }

    public static OutboxEvent of(GroupRuleEntity entity) {
        return GroupRuleConfigured.of(entity.groupId, entity.type,
                new RuleConfigurationDto(entity.configuration));
    }

    public static OutboxEvent of(ArtifactRuleEntity entity) {
        return ArtifactRuleConfigured.of(entity.groupId, entity.artifactId, entity.type,
                new RuleConfigurationDto(entity.configuration));
    }

    /**
     * Global contract rules are stored with the {@link ContractRulesetConfigured#GLOBAL_COORDINATE}
     * coordinates, so they produce the same event as {@link ContractRulesetConfigured#ofGlobal}.
     *
     * @param version the version the rule belongs to, or null for an artifact-level or global rule
     */
    public static OutboxEvent of(ContractRuleEntity entity, String version) {
        return ContractRulesetConfigured.of(entity.groupId, entity.artifactId, version,
                ContractRulesetConfigured.Action.SET);
    }
}
