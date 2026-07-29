/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.events.GroupCreated;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GroupCreatedCloudEventTest {

    @Test
    public void testFromGroupCreated() {
        GroupMetaDataDto metaDataDto = new GroupMetaDataDto();
        metaDataDto.setGroupId("test-group");

        GroupCreated event = GroupCreated.of(metaDataDto);
        String source = "/apicurio-registry";

        GroupCreatedCloudEvent cloudEvent = GroupCreatedCloudEvent.from(event, source);

        assertNotNull(cloudEvent);
        assertNotNull(cloudEvent.getCloudEvent());
        assertEquals(event.getId(), cloudEvent.getCloudEvent().getId());
        assertEquals(source, cloudEvent.getCloudEvent().getSource());
        assertEquals("io.apicurio.registry.events.GroupCreated", cloudEvent.getCloudEvent().getType());
        assertEquals("1.0", cloudEvent.getCloudEvent().getSpecversion());
        assertEquals("application/json", cloudEvent.getCloudEvent().getDatacontenttype());
        assertEquals(event.getTimestamp(), cloudEvent.getCloudEvent().getTime());
        assertNotNull(cloudEvent.getCloudEvent().getData());
    }

    @Test
    public void testCloudEventTypeFormat() {
        GroupMetaDataDto metaDataDto = new GroupMetaDataDto();
        metaDataDto.setGroupId("test-group");

        GroupCreated event = GroupCreated.of(metaDataDto);
        GroupCreatedCloudEvent cloudEvent = GroupCreatedCloudEvent.from(event, "/apicurio-registry");

        assertEquals("io.apicurio.registry.events.GroupCreated", cloudEvent.getCloudEvent().getType());
    }
}
