/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.events.ArtifactCreated;
import io.apicurio.registry.events.ArtifactDeleted;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CloudEventConverterTest {

    private static final String SOURCE = "/apicurio-registry";

    /** Every StorageEventType that represents a registry data change. READY is intentionally excluded. */
    private static final List<StorageEventType> DATA_CHANGE_EVENTS = List.of(
            StorageEventType.ARTIFACT_CREATED,
            StorageEventType.ARTIFACT_DELETED,
            StorageEventType.ARTIFACT_METADATA_UPDATED,
            StorageEventType.ARTIFACT_RULE_CONFIGURED,
            StorageEventType.ARTIFACT_VERSION_CREATED,
            StorageEventType.ARTIFACT_VERSION_DELETED,
            StorageEventType.ARTIFACT_VERSION_METADATA_UPDATED,
            StorageEventType.ARTIFACT_VERSION_STATE_CHANGED,
            StorageEventType.GROUP_CREATED,
            StorageEventType.GROUP_DELETED,
            StorageEventType.GROUP_METADATA_UPDATED,
            StorageEventType.GROUP_RULE_CONFIGURED,
            StorageEventType.GLOBAL_RULE_CONFIGURED,
            StorageEventType.CONTRACT_RULESET_CONFIGURED,
            StorageEventType.CONTRACT_METADATA_UPDATED,
            StorageEventType.CONTRACT_STATUS_CHANGED);

    private static String cloudEventType(StorageEventType eventType) {
        return "io.apicurio.registry." + eventType.name().toLowerCase(Locale.ROOT).replace('_', '.');
    }

    private static OutboxEvent eventOfType(StorageEventType eventType) {
        return new OutboxEvent("id-" + eventType.name(), "aggregate-" + eventType.name(), Instant.now()) {
            @Override
            public JSONObject getPayload() {
                return new JSONObject().put("eventType", eventType.name());
            }

            @Override
            public String getType() {
                return eventType.name();
            }
        };
    }

    @Test
    public void testConvertArtifactCreated() {
        long createdOn = System.currentTimeMillis();
        Instant expectedTimestamp = Instant.ofEpochMilli(createdOn);

        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setCreatedOn(createdOn);

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, SOURCE);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(SOURCE, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.artifact.created", cloudEvent.getType());
        assertEquals(expectedTimestamp, cloudEvent.getTime());
    }

    @Test
    public void testConvertArtifactDeleted() {
        Instant expectedTimestamp = Instant.parse("2024-05-01T10:15:30Z");
        ArtifactDeleted event = ArtifactDeleted.of("test-group", "test-artifact", expectedTimestamp);

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, SOURCE);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(SOURCE, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.artifact.deleted", cloudEvent.getType());
        assertEquals(expectedTimestamp, cloudEvent.getTime());
    }

    @Test
    public void testAllDataChangeEventsAreMapped() {
        for (StorageEventType eventType : DATA_CHANGE_EVENTS) {
            CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(eventOfType(eventType), SOURCE);
            assertNotNull(cloudEvent, "No CloudEvent mapping for " + eventType);
            assertEquals(cloudEventType(eventType), cloudEvent.getType(),
                    "Unexpected CloudEvent type for " + eventType);
        }
    }

    @Test
    public void testConvertUnsupportedEventType() {
        OutboxEvent unsupportedEvent = new OutboxEvent("test-id", "test-aggregate", Instant.now()) {
            @Override
            public JSONObject getPayload() {
                return new JSONObject();
            }

            @Override
            public String getType() {
                return "UNSUPPORTED_EVENT_TYPE";
            }
        };

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(unsupportedEvent, SOURCE);

        assertNull(cloudEvent);
    }

    @Test
    public void testReadyIsNotMapped() {
        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(eventOfType(StorageEventType.READY), SOURCE);

        assertNull(cloudEvent);
    }

    @Test
    public void testConvertWithCustomSourceProvider() {
        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setCreatedOn(System.currentTimeMillis());

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, e -> "/custom-source");

        assertNotNull(cloudEvent);
        assertEquals("/custom-source", cloudEvent.getSource());
    }
}
