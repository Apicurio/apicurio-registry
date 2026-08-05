/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Utility class for converting registry outbox events to CloudEvents format.
 */
public class CloudEventConverter {

    private static final Logger log = LoggerFactory.getLogger(CloudEventConverter.class);

    private CloudEventConverter() {
    }

    public static CloudEventDto toCloudEvent(OutboxEvent event, String source) {
        StorageEventType eventType;
        try {
            eventType = StorageEventType.valueOf(event.getType());
        } catch (IllegalArgumentException e) {
            log.warn("Dropping outbox event {} with unknown type: {}", event.getId(), event.getType());
            return null;
        }
        return switch (eventType) {
            case ARTIFACT_CREATED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ArtifactCreated");
            case ARTIFACT_DELETED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ArtifactDeleted");
            case ARTIFACT_METADATA_UPDATED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ArtifactMetadataUpdated");
            case ARTIFACT_RULE_CONFIGURED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ArtifactRuleConfigured");
            case ARTIFACT_VERSION_CREATED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ArtifactVersionCreated");
            case ARTIFACT_VERSION_DELETED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ArtifactVersionDeleted");
            case ARTIFACT_VERSION_METADATA_UPDATED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ArtifactVersionMetadataUpdated");
            case ARTIFACT_VERSION_STATE_CHANGED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ArtifactVersionStateChanged");
            case GLOBAL_RULE_CONFIGURED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.GlobalRuleConfigured");
            case GROUP_CREATED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.GroupCreated");
            case GROUP_DELETED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.GroupDeleted");
            case GROUP_METADATA_UPDATED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.GroupMetadataUpdated");
            case GROUP_RULE_CONFIGURED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.GroupRuleConfigured");
            case CONTRACT_RULESET_CONFIGURED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ContractRulesetConfigured");
            case CONTRACT_METADATA_UPDATED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ContractMetadataUpdated");
            case CONTRACT_STATUS_CHANGED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ContractStatusChanged");
            default -> {
                log.warn("No CloudEvent mapping for event type: {}, dropping event {}", eventType, event.getId());
                yield null;
            }
        };
    }

    public static CloudEventDto toCloudEvent(OutboxEvent event, Function<OutboxEvent, String> sourceProvider) {
        String source = sourceProvider.apply(event);
        return toCloudEvent(event, source);
    }
}
