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
 * CloudEvent wrapper for ArtifactCreated event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class ArtifactCreatedCloudEvent {

    private CloudEventDto cloudEvent;

    public ArtifactCreatedCloudEvent() {
    }

    public static ArtifactCreatedCloudEvent from(io.apicurio.registry.events.ArtifactCreated event, String source) {
        CloudEventDto dto = new CloudEventDto()
                .withId(event.getId())
                .withSource(source)
                .withType("io.apicurio.registry.events.ArtifactCreated")
                .withTime(Instant.now())
                .withData(event.getPayload());

        ArtifactCreatedCloudEvent wrapper = new ArtifactCreatedCloudEvent();
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
