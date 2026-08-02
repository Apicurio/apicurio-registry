/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.webhook;

import io.apicurio.registry.events.ArtifactCreated;
import io.apicurio.registry.events.dto.CloudEventConverter;
import io.apicurio.registry.events.dto.CloudEventDto;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.sql.SqlOutboxEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(WebhookEnabledTestProfile.class)
public class WebhookDeliveryServiceTest {

    @Inject
    WebhookDeliveryService deliveryService;

    @Test
    public void testOnOutboxEvent() {
        long createdOn = System.currentTimeMillis();

        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setCreatedOn(createdOn);

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);
        SqlOutboxEvent sqlOutboxEvent = SqlOutboxEvent.of(event);

        // Smoke test: the observer path converts and logs the event without throwing.
        // Intentionally assertion-free beyond this until actual webhook delivery is wired.
        assertDoesNotThrow(() -> deliveryService.onOutboxEvent(sqlOutboxEvent));
    }

    @Test
    public void testCloudEventConversion() {
        long createdOn = System.currentTimeMillis();

        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setCreatedOn(createdOn);

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);
        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, "/apicurio-registry");

        assertNotNull(cloudEvent);
        assertEquals("io.apicurio.registry.events.ArtifactCreated", cloudEvent.getType());
        assertEquals("/apicurio-registry", cloudEvent.getSource());
        assertEquals(event.getId(), cloudEvent.getId());
        assertEquals("1.0", cloudEvent.getSpecversion());
    }
}
