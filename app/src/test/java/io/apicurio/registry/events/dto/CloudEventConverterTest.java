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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CloudEventConverterTest {

    @Test
    public void testConvertArtifactCreated() {
        long createdOn = System.currentTimeMillis();
        Instant expectedTimestamp = Instant.ofEpochMilli(createdOn);

        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setCreatedOn(createdOn);

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.ArtifactCreated", cloudEvent.getType());
        assertEquals(expectedTimestamp, cloudEvent.getTime());
    }

    @Test
    public void testConvertArtifactDeleted() {
        Instant expectedTimestamp = Instant.parse("2024-05-01T10:15:30Z");
        ArtifactDeleted event = ArtifactDeleted.of("test-group", "test-artifact", expectedTimestamp);
        String source = "/apicurio-registry";

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, source);

        assertNotNull(cloudEvent);
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals("io.apicurio.registry.events.ArtifactDeleted", cloudEvent.getType());
        assertEquals(expectedTimestamp, cloudEvent.getTime());
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

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(unsupportedEvent, "/apicurio-registry");

        assertNull(cloudEvent);
    }

    @Test
    public void testConvertUnmappedStorageEventType() {
        OutboxEvent unmappedEvent = new OutboxEvent("test-id", "test-aggregate", Instant.now()) {
            @Override
            public JSONObject getPayload() {
                return new JSONObject();
            }

            @Override
            public String getType() {
                return StorageEventType.READY.name();
            }
        };

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(unmappedEvent, "/apicurio-registry");

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
