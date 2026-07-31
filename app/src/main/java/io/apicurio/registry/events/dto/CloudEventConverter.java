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
 * Utility class for converting registry events to CloudEvents format.
 * <p>
 * This class provides the integration path between the existing event system
 * and CloudEvents-compliant webhook delivery. It converts {@link OutboxEvent}
 * instances to their corresponding CloudEvent wrapper classes.
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
     * @return the CloudEvent DTO, or null if the event type is not supported
     */
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
            case ARTIFACT_VERSION_STATE_CHANGED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.ArtifactVersionStateChanged");
            case GLOBAL_RULE_CONFIGURED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.GlobalRuleConfigured");
            case GROUP_CREATED -> CloudEventDto.from(event, source, "io.apicurio.registry.events.GroupCreated");
            default -> {
                log.warn("No CloudEvent mapping for event type: {}, dropping event {}", eventType, event.getId());
                yield null;
            }
        };
    }

    /**
     * Converts an OutboxEvent to its CloudEvent representation using a custom source.
     *
     * @param event the outbox event to convert
     * @param sourceProvider a function that provides the source URI based on the event
     * @return the CloudEvent DTO, or null if the event type is not supported
     */
    public static CloudEventDto toCloudEvent(OutboxEvent event, Function<OutboxEvent, String> sourceProvider) {
        String source = sourceProvider.apply(event);
        return toCloudEvent(event, source);
    }
}
