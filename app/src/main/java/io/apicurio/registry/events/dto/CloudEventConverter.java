/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.events.ArtifactCreated;
import io.apicurio.registry.events.ArtifactDeleted;
import io.apicurio.registry.events.ArtifactMetadataUpdated;
import io.apicurio.registry.events.ArtifactRuleConfigured;
import io.apicurio.registry.events.ArtifactVersionCreated;
import io.apicurio.registry.events.ArtifactVersionStateChanged;
import io.apicurio.registry.events.GlobalRuleConfigured;
import io.apicurio.registry.events.GroupCreated;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.function.Function;

/**
 * Utility class for converting registry events to CloudEvents format.
 * <p>
 * This class provides the integration path between the existing event system
 * and CloudEvents-compliant webhook delivery. It converts {@link OutboxEvent}
 * instances to their corresponding CloudEvent wrapper classes.
 */
public class CloudEventConverter {

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
        return switch (event.getType()) {
            case "ARTIFACT_CREATED" -> ArtifactCreatedCloudEvent.from((ArtifactCreated) event, source).getCloudEvent();
            case "ARTIFACT_DELETED" -> ArtifactDeletedCloudEvent.from((ArtifactDeleted) event, source).getCloudEvent();
            case "ARTIFACT_METADATA_UPDATED" -> ArtifactMetadataUpdatedCloudEvent.from((ArtifactMetadataUpdated) event, source).getCloudEvent();
            case "ARTIFACT_RULE_CONFIGURED" -> ArtifactRuleConfiguredCloudEvent.from((ArtifactRuleConfigured) event, source).getCloudEvent();
            case "ARTIFACT_VERSION_CREATED" -> ArtifactVersionCreatedCloudEvent.from((ArtifactVersionCreated) event, source).getCloudEvent();
            case "ARTIFACT_VERSION_STATE_CHANGED" -> ArtifactVersionStateChangedCloudEvent.from((ArtifactVersionStateChanged) event, source).getCloudEvent();
            case "GLOBAL_RULE_CONFIGURED" -> GlobalRuleConfiguredCloudEvent.from((GlobalRuleConfigured) event, source).getCloudEvent();
            case "GROUP_CREATED" -> GroupCreatedCloudEvent.from((GroupCreated) event, source).getCloudEvent();
            default -> null;
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
