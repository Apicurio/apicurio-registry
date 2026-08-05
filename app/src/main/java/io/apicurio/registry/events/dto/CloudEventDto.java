/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.storage.dto.OutboxEvent;

import java.time.Instant;

/**
 * CloudEvents-compliant data transfer object for registry outbox events.
 */
public class CloudEventDto {

    private final String specversion;
    private final String id;
    private final String source;
    private final String type;
    private final String datacontenttype;
    private final Object data;
    private final Instant time;

    private CloudEventDto(String id, String source, String type, String datacontenttype, Object data, Instant time) {
        this.specversion = "1.0";
        this.id = id;
        this.source = source;
        this.type = type;
        this.datacontenttype = datacontenttype;
        this.data = data;
        this.time = time;
    }

    public static CloudEventDto from(OutboxEvent event, String source, String type) {
        return new CloudEventDto(event.getId(), source, type, "application/json", event.getPayload().toString(), Instant.now());
    }

    public String getSpecversion() {
        return specversion;
    }

    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getType() {
        return type;
    }

    public String getDatacontenttype() {
        return datacontenttype;
    }

    public Object getData() {
        return data;
    }

    public Instant getTime() {
        return time;
    }
}
