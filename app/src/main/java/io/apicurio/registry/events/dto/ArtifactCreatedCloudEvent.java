/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

/**
 * CloudEvent wrapper for ArtifactCreated event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class ArtifactCreatedCloudEvent {

    @JsonUnwrapped
    private CloudEventDto cloudEvent;

    public ArtifactCreatedCloudEvent() {
    }

    public static ArtifactCreatedCloudEvent from(io.apicurio.registry.events.ArtifactCreated event, String source) {
        Instant eventTime = extractTimestampFromPayload(event.getPayload());
        
        CloudEventDto dto = new CloudEventDto()
                .withId(event.getId())
                .withSource(source)
                .withType("io.apicurio.registry.events.ArtifactCreated")
                .withTime(eventTime)
                .withData(event.getPayload());

        ArtifactCreatedCloudEvent wrapper = new ArtifactCreatedCloudEvent();
        wrapper.setCloudEvent(dto);
        return wrapper;
    }

    private static Instant extractTimestampFromPayload(org.json.JSONObject payload) {
        if (payload != null && payload.has("createdOn")) {
            long createdOn = payload.getLong("createdOn");
            return Instant.ofEpochMilli(createdOn);
        }
        return Instant.now();
    }

    public CloudEventDto getCloudEvent() {
        return cloudEvent;
    }

    public void setCloudEvent(CloudEventDto cloudEvent) {
        this.cloudEvent = cloudEvent;
    }
}
