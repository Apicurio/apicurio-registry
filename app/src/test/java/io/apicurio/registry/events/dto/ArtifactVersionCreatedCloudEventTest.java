/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.events.ArtifactVersionCreated;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ArtifactVersionCreatedCloudEventTest {

    @Test
    public void testFromArtifactVersionCreated() {
        ArtifactVersionMetaDataDto metaDataDto = new ArtifactVersionMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setVersion("1.0.0");
        metaDataDto.setName("Test Artifact");
        metaDataDto.setDescription("Test Description");

        ArtifactVersionCreated event = ArtifactVersionCreated.of(metaDataDto);
        String source = "/apicurio-registry";

        ArtifactVersionCreatedCloudEvent cloudEvent = ArtifactVersionCreatedCloudEvent.from(event, source);

        assertNotNull(cloudEvent);
        assertNotNull(cloudEvent.getCloudEvent());
        assertEquals(event.getId(), cloudEvent.getCloudEvent().getId());
        assertEquals(source, cloudEvent.getCloudEvent().getSource());
        assertEquals("io.apicurio.registry.events.ArtifactVersionCreated", cloudEvent.getCloudEvent().getType());
        assertEquals("1.0", cloudEvent.getCloudEvent().getSpecversion());
        assertEquals("application/json", cloudEvent.getCloudEvent().getDatacontenttype());
        assertEquals(event.getTimestamp(), cloudEvent.getCloudEvent().getTime());
        assertNotNull(cloudEvent.getCloudEvent().getData());
    }

    @Test
    public void testCloudEventTypeFormat() {
        ArtifactVersionMetaDataDto metaDataDto = new ArtifactVersionMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setVersion("1.0.0");

        ArtifactVersionCreated event = ArtifactVersionCreated.of(metaDataDto);
        ArtifactVersionCreatedCloudEvent cloudEvent = ArtifactVersionCreatedCloudEvent.from(event, "/apicurio-registry");

        assertEquals("io.apicurio.registry.events.ArtifactVersionCreated", cloudEvent.getCloudEvent().getType());
    }
}
