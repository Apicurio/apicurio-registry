/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CloudEventConverterTest {

    @Test
    public void testConvertArtifactCreated() {
        OutboxEvent event = createOutboxEvent(StorageEventType.ARTIFACT_CREATED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.ArtifactCreated", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertArtifactDeleted() {
        OutboxEvent event = createOutboxEvent(StorageEventType.ARTIFACT_DELETED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.ArtifactDeleted", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertGroupDeleted() {
        OutboxEvent event = createOutboxEvent(StorageEventType.GROUP_DELETED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.GroupDeleted", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertGroupMetadataUpdated() {
        OutboxEvent event = createOutboxEvent(StorageEventType.GROUP_METADATA_UPDATED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.GroupMetadataUpdated", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertGroupRuleConfigured() {
        OutboxEvent event = createOutboxEvent(StorageEventType.GROUP_RULE_CONFIGURED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.GroupRuleConfigured", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertArtifactVersionDeleted() {
        OutboxEvent event = createOutboxEvent(StorageEventType.ARTIFACT_VERSION_DELETED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.ArtifactVersionDeleted", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertArtifactVersionMetadataUpdated() {
        OutboxEvent event = createOutboxEvent(StorageEventType.ARTIFACT_VERSION_METADATA_UPDATED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.ArtifactVersionMetadataUpdated", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertContractRulesetConfigured() {
        OutboxEvent event = createOutboxEvent(StorageEventType.CONTRACT_RULESET_CONFIGURED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.ContractRulesetConfigured", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertContractMetadataUpdated() {
        OutboxEvent event = createOutboxEvent(StorageEventType.CONTRACT_METADATA_UPDATED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.ContractMetadataUpdated", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertContractStatusChanged() {
        OutboxEvent event = createOutboxEvent(StorageEventType.CONTRACT_STATUS_CHANGED);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.ContractStatusChanged", cloudEvent.getType());
        assertNotNull(cloudEvent.getTime());
    }

    @Test
    public void testConvertUnsupportedEventType() {
        OutboxEvent unsupportedEvent = new OutboxEvent(UUID.randomUUID().toString(), "test-aggregate") {
            @Override
            public JSONObject getPayload() {
                return new JSONObject();
            }

            @Override
            public String getType() {
                return "UNSUPPORTED_EVENT_TYPE";
            }
        };

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(unsupportedEvent, "/apicurio-registry");

        assertNull(cloudEvent);
    }

    @Test
    public void testConvertUnmappedStorageEventType() {
        OutboxEvent unmappedEvent = createOutboxEvent(StorageEventType.READY);

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(unmappedEvent, "/apicurio-registry");

        assertNull(cloudEvent);
    }

    @Test
    public void testConvertWithCustomSourceProvider() {
        OutboxEvent event = createOutboxEvent(StorageEventType.ARTIFACT_CREATED);

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, e -> "/custom-source");

        assertNotNull(cloudEvent);
        assertEquals("/custom-source", cloudEvent.getSource());
    }

    private OutboxEvent createOutboxEvent(StorageEventType eventType) {
        return new OutboxEvent(UUID.randomUUID().toString(), "test-aggregate") {
            @Override
            public JSONObject getPayload() {
                return new JSONObject();
            }

            @Override
            public String getType() {
                return eventType.name();
            }
        };
    }
}
