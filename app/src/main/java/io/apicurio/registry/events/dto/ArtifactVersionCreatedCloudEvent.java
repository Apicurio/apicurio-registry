/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * CloudEvent wrapper for ArtifactVersionCreated event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class ArtifactVersionCreatedCloudEvent {

    @JsonUnwrapped
    private CloudEventDto cloudEvent;

    public ArtifactVersionCreatedCloudEvent() {
    }

    public static ArtifactVersionCreatedCloudEvent from(io.apicurio.registry.events.ArtifactVersionCreated event, String source) {
        CloudEventDto dto = new CloudEventDto()
                .withId(event.getId())
                .withSource(source)
                .withType("io.apicurio.registry.events.ArtifactVersionCreated")
                .withTime(event.getTimestamp())
                .withData(event.getPayload());

        ArtifactVersionCreatedCloudEvent wrapper = new ArtifactVersionCreatedCloudEvent();
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
