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
 * Utility class for converting registry outbox events to CloudEvents 1.0.
 * <p>
 * Every {@link StorageEventType} that represents a registry data change is mapped to a CloudEvent
 * with a reverse-DNS type string (e.g. {@code io.apicurio.registry.artifact.created}). The
 * {@link StorageEventType#READY} startup signal is not a data-change event and is deliberately
 * left unmapped.
 */
public class CloudEventConverter {

    private static final Logger log = LoggerFactory.getLogger(CloudEventConverter.class);

    private CloudEventConverter() {
    }

    /**
     * Converts an OutboxEvent to its CloudEvent representation.
     *
     * @param event the outbox event to convert
     * @param source the event source URI (e.g., "/apicurio-registry")
     * @return the CloudEvent DTO, or null if the event type is unknown or not a data-change event
     */
    public static CloudEventDto toCloudEvent(OutboxEvent event, String source) {
        StorageEventType eventType;
        try {
            eventType = StorageEventType.valueOf(event.getType());
        } catch (IllegalArgumentException e) {
            log.warn("Dropping outbox event {} with unknown type: {}", event.getId(), event.getType());
            return null;
        }
        String cloudEventType = switch (eventType) {
            case ARTIFACT_CREATED -> "io.apicurio.registry.artifact.created";
            case ARTIFACT_DELETED -> "io.apicurio.registry.artifact.deleted";
            case ARTIFACT_METADATA_UPDATED -> "io.apicurio.registry.artifact.metadata.updated";
            case ARTIFACT_RULE_CONFIGURED -> "io.apicurio.registry.artifact.rule.configured";
            case ARTIFACT_VERSION_CREATED -> "io.apicurio.registry.artifact.version.created";
            case ARTIFACT_VERSION_DELETED -> "io.apicurio.registry.artifact.version.deleted";
            case ARTIFACT_VERSION_METADATA_UPDATED -> "io.apicurio.registry.artifact.version.metadata.updated";
            case ARTIFACT_VERSION_STATE_CHANGED -> "io.apicurio.registry.artifact.version.state.changed";
            case GROUP_CREATED -> "io.apicurio.registry.group.created";
            case GROUP_DELETED -> "io.apicurio.registry.group.deleted";
            case GROUP_METADATA_UPDATED -> "io.apicurio.registry.group.metadata.updated";
            case GROUP_RULE_CONFIGURED -> "io.apicurio.registry.group.rule.configured";
            case GLOBAL_RULE_CONFIGURED -> "io.apicurio.registry.global.rule.configured";
            case CONTRACT_RULESET_CONFIGURED -> "io.apicurio.registry.contract.ruleset.configured";
            case CONTRACT_METADATA_UPDATED -> "io.apicurio.registry.contract.metadata.updated";
            case CONTRACT_STATUS_CHANGED -> "io.apicurio.registry.contract.status.changed";
            case READY -> null;
        };
        if (cloudEventType == null) {
            log.warn("No CloudEvent mapping for event type: {}, dropping event {}", eventType, event.getId());
            return null;
        }
        return CloudEventDto.from(event, source, cloudEventType);
    }

    /**
     * Converts an OutboxEvent to its CloudEvent representation using a custom source.
     *
     * @param event the outbox event to convert
     * @param sourceProvider a function that provides the source URI based on the event
     * @return the CloudEvent DTO, or null if the event type is unknown or not a data-change event
     */
    public static CloudEventDto toCloudEvent(OutboxEvent event, Function<OutboxEvent, String> sourceProvider) {
        String source = sourceProvider.apply(event);
        return toCloudEvent(event, source);
    }
}
