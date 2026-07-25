/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

/**
 * CloudEvent wrapper for GroupCreated event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class GroupCreatedCloudEvent {

    private CloudEventDto cloudEvent;

    public GroupCreatedCloudEvent() {
    }

    public static GroupCreatedCloudEvent from(io.apicurio.registry.events.GroupCreated event, String source) {
        CloudEventDto dto = new CloudEventDto()
                .withId(event.getId())
                .withSource(source)
                .withType("io.apicurio.registry.events.GroupCreated")
                .withTime(Instant.now())
                .withData(event.getPayload());

        GroupCreatedCloudEvent wrapper = new GroupCreatedCloudEvent();
        wrapper.setCloudEvent(dto);
        return wrapper;
    }

    public CloudEventDto getCloudEvent() {
        return cloudEvent;
    }

    public void setCloudEvent(CloudEventDto cloudEvent) {
        this.cloudEvent = cloudEvent;
    }
}
