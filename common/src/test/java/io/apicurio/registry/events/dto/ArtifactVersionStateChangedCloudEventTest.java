/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.events.ArtifactVersionStateChanged;
import io.apicurio.registry.types.VersionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ArtifactVersionStateChangedCloudEventTest {

    @Test
    public void testFromArtifactVersionStateChanged() {
        ArtifactVersionStateChanged event = ArtifactVersionStateChanged.of("test-group", "test-artifact", "1.0.0", VersionState.ENABLED, VersionState.DEPRECATED);
        String source = "/apicurio-registry";

        ArtifactVersionStateChangedCloudEvent cloudEvent = ArtifactVersionStateChangedCloudEvent.from(event, source);

        assertNotNull(cloudEvent);
        assertNotNull(cloudEvent.getCloudEvent());
        assertEquals(event.getId(), cloudEvent.getCloudEvent().getId());
        assertEquals(source, cloudEvent.getCloudEvent().getSource());
        assertEquals("io.apicurio.registry.events.ArtifactVersionStateChanged", cloudEvent.getCloudEvent().getType());
        assertEquals("1.0", cloudEvent.getCloudEvent().getSpecversion());
        assertEquals("application/json", cloudEvent.getCloudEvent().getDatacontenttype());
        assertNotNull(cloudEvent.getCloudEvent().getTime());
        assertNotNull(cloudEvent.getCloudEvent().getData());
    }

    @Test
    public void testCloudEventTypeFormat() {
        ArtifactVersionStateChanged event = ArtifactVersionStateChanged.of("test-group", "test-artifact", "1.0.0", VersionState.ENABLED, VersionState.DEPRECATED);
        ArtifactVersionStateChangedCloudEvent cloudEvent = ArtifactVersionStateChangedCloudEvent.from(event, "/apicurio-registry");

        assertEquals("io.apicurio.registry.events.ArtifactVersionStateChanged", cloudEvent.getCloudEvent().getType());
    }
}
