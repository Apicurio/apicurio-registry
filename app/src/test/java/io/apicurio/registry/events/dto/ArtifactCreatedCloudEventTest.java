/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.events.ArtifactCreated;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ArtifactCreatedCloudEventTest {

    @Test
    public void testFromArtifactCreated() {
        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setName("Test Artifact");
        metaDataDto.setDescription("Test Description");

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);
        String source = "/apicurio-registry";

        ArtifactCreatedCloudEvent cloudEvent = ArtifactCreatedCloudEvent.from(event, source);

        assertNotNull(cloudEvent);
        assertNotNull(cloudEvent.getCloudEvent());
        assertEquals(event.getId(), cloudEvent.getCloudEvent().getId());
        assertEquals(source, cloudEvent.getCloudEvent().getSource());
        assertEquals("io.apicurio.registry.events.ArtifactCreated", cloudEvent.getCloudEvent().getType());
        assertEquals("1.0", cloudEvent.getCloudEvent().getSpecversion());
        assertEquals("application/json", cloudEvent.getCloudEvent().getDatacontenttype());
        assertNotNull(cloudEvent.getCloudEvent().getTime());
        assertNotNull(cloudEvent.getCloudEvent().getData());
    }

    @Test
    public void testCloudEventTypeFormat() {
        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);
        ArtifactCreatedCloudEvent cloudEvent = ArtifactCreatedCloudEvent.from(event, "/apicurio-registry");

        assertEquals("io.apicurio.registry.events.ArtifactCreated", cloudEvent.getCloudEvent().getType());
    }
}
