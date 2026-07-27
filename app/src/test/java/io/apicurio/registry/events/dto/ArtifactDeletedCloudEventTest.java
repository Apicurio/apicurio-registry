/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.events.ArtifactDeleted;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ArtifactDeletedCloudEventTest {

    @Test
    public void testFromArtifactDeleted() {
        ArtifactDeleted event = ArtifactDeleted.of("test-group", "test-artifact");
        String source = "/apicurio-registry";

        ArtifactDeletedCloudEvent cloudEvent = ArtifactDeletedCloudEvent.from(event, source);

        assertNotNull(cloudEvent);
        assertNotNull(cloudEvent.getCloudEvent());
        assertEquals(event.getId(), cloudEvent.getCloudEvent().getId());
        assertEquals(source, cloudEvent.getCloudEvent().getSource());
        assertEquals("io.apicurio.registry.events.ArtifactDeleted", cloudEvent.getCloudEvent().getType());
        assertEquals("1.0", cloudEvent.getCloudEvent().getSpecversion());
        assertEquals("application/json", cloudEvent.getCloudEvent().getDatacontenttype());
        assertNotNull(cloudEvent.getCloudEvent().getTime());
        assertNotNull(cloudEvent.getCloudEvent().getData());
    }

    @Test
    public void testCloudEventTypeFormat() {
        ArtifactDeleted event = ArtifactDeleted.of("test-group", "test-artifact");
        ArtifactDeletedCloudEvent cloudEvent = ArtifactDeletedCloudEvent.from(event, "/apicurio-registry");

        assertEquals("io.apicurio.registry.events.ArtifactDeleted", cloudEvent.getCloudEvent().getType());
    }
}
