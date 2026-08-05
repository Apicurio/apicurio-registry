/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.apicurio.registry.storage.dto.OutboxEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.json.JSONObject;

import java.time.Instant;

/**
 * CloudEvents 1.0 specification-compliant data transfer object.
 * <p>
 * This class represents a CloudEvent with all required and optional fields
 * as defined in the CloudEvents 1.0 specification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "specversion", "id", "source", "type", "datacontenttype", "data", "time" })
@RegisterForReflection
public class CloudEventDto {

    @JsonProperty("specversion")
    private String specversion = "1.0";

    @JsonProperty("id")
    private String id;

    @JsonProperty("source")
    private String source;

    @JsonProperty("type")
    private String type;

    @JsonProperty("datacontenttype")
    private String datacontenttype = "application/json";

    @JsonProperty("data")
    private Object data;

    @JsonProperty("time")
    private Instant time;

    public CloudEventDto() {
    }

    public CloudEventDto withId(String id) {
        this.id = id;
        return this;
    }

    public CloudEventDto withSource(String source) {
        this.source = source;
        return this;
    }

    public CloudEventDto withType(String type) {
        this.type = type;
        return this;
    }

    public CloudEventDto withDatacontenttype(String datacontenttype) {
        this.datacontenttype = datacontenttype;
        return this;
    }

    public CloudEventDto withData(Object data) {
        this.data = normalizeData(data);
        return this;
    }

    public CloudEventDto withTime(Instant time) {
        this.time = time;
        return this;
    }

    public CloudEventDto withSpecversion(String specversion) {
        this.specversion = specversion;
        return this;
    }

    public String getSpecversion() {
        return specversion;
    }

    public void setSpecversion(String specversion) {
        this.specversion = specversion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDatacontenttype() {
        return datacontenttype;
    }

    public void setDatacontenttype(String datacontenttype) {
        this.datacontenttype = datacontenttype;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = normalizeData(data);
    }

    /**
     * Registry events carry their payload as an {@link JSONObject}, which Jackson does not
     * understand: it introspects the bean properties and emits {@code {"mapType":...,"empty":...}}
     * instead of the payload. Convert to a plain {@link java.util.Map} so the payload survives
     * serialization by any Jackson {@code ObjectMapper}.
     */
    private static Object normalizeData(Object data) {
        if (data instanceof JSONObject jsonObject) {
            return jsonObject.toMap();
        }
        return data;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    /**
     * Factory method to create a CloudEventDto from an OutboxEvent.
     * <p>
     * Validates that the resulting event carries all CloudEvents 1.0 required attributes
     * ({@code id}, {@code source}, {@code type}) so callers cannot construct a spec-invalid
     * event whose required fields silently disappear from the wire output.
     *
     * @param event the outbox event
     * @param source the event source URI (e.g., "/apicurio-registry")
     * @param eventType the CloudEvent type string (e.g., "io.apicurio.registry.artifact.created")
     * @return the CloudEventDto
     * @throws IllegalArgumentException if the event is null or any required attribute is null/blank
     */
    public static CloudEventDto from(OutboxEvent event, String source, String eventType) {
        if (event == null) {
            throw new IllegalArgumentException("OutboxEvent must not be null");
        }
        CloudEventDto dto = new CloudEventDto()
                .withId(event.getId())
                .withSource(source)
                .withType(eventType)
                .withTime(event.getTimestamp())
                .withData(event.getPayload());
        if (isBlank(dto.getId())) {
            throw new IllegalArgumentException("CloudEvent 'id' is a required attribute and must not be blank");
        }
        if (isBlank(dto.getSource())) {
            throw new IllegalArgumentException("CloudEvent 'source' is a required attribute and must not be blank");
        }
        if (isBlank(dto.getType())) {
            throw new IllegalArgumentException("CloudEvent 'type' is a required attribute and must not be blank");
        }
        return dto;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
