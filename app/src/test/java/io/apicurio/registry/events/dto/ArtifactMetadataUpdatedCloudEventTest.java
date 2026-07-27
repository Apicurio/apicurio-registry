/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.events.ArtifactMetadataUpdated;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ArtifactMetadataUpdatedCloudEventTest {

    @Test
    public void testFromArtifactMetadataUpdated() {
        EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
        metaDataDto.setName("Updated Name");
        metaDataDto.setOwner("test-owner");
        metaDataDto.setDescription("Updated Description");

        ArtifactMetadataUpdated event = ArtifactMetadataUpdated.of("test-group", "test-artifact", metaDataDto);
        String source = "/apicurio-registry";

        ArtifactMetadataUpdatedCloudEvent cloudEvent = ArtifactMetadataUpdatedCloudEvent.from(event, source);

        assertNotNull(cloudEvent);
        assertNotNull(cloudEvent.getCloudEvent());
        assertEquals(event.getId(), cloudEvent.getCloudEvent().getId());
        assertEquals(source, cloudEvent.getCloudEvent().getSource());
        assertEquals("io.apicurio.registry.events.ArtifactMetadataUpdated", cloudEvent.getCloudEvent().getType());
        assertEquals("1.0", cloudEvent.getCloudEvent().getSpecversion());
        assertEquals("application/json", cloudEvent.getCloudEvent().getDatacontenttype());
        assertNotNull(cloudEvent.getCloudEvent().getTime());
        assertNotNull(cloudEvent.getCloudEvent().getData());
    }

    @Test
    public void testCloudEventTypeFormat() {
        EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
        metaDataDto.setName("Updated Name");

        ArtifactMetadataUpdated event = ArtifactMetadataUpdated.of("test-group", "test-artifact", metaDataDto);
        ArtifactMetadataUpdatedCloudEvent cloudEvent = ArtifactMetadataUpdatedCloudEvent.from(event, "/apicurio-registry");

        assertEquals("io.apicurio.registry.events.ArtifactMetadataUpdated", cloudEvent.getCloudEvent().getType());
    }
}
