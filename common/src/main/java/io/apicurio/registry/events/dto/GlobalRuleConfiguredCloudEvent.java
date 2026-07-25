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
 * CloudEvent wrapper for GlobalRuleConfigured event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class GlobalRuleConfiguredCloudEvent {

    private CloudEventDto cloudEvent;

    public GlobalRuleConfiguredCloudEvent() {
    }

    public static GlobalRuleConfiguredCloudEvent from(io.apicurio.registry.events.GlobalRuleConfigured event, String source) {
        CloudEventDto dto = new CloudEventDto()
                .withId(event.getId())
                .withSource(source)
                .withType("io.apicurio.registry.events.GlobalRuleConfigured")
                .withTime(Instant.now())
                .withData(event.getPayload());

        GlobalRuleConfiguredCloudEvent wrapper = new GlobalRuleConfiguredCloudEvent();
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
