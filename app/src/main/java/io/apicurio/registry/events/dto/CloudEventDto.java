/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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

    @JsonProperty("specversion")
    public String getSpecversion() {
        return specversion;
    }

    @JsonProperty("specversion")
    public void setSpecversion(String specversion) {
        this.specversion = specversion;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("source")
    public String getSource() {
        return source;
    }

    @JsonProperty("source")
    public void setSource(String source) {
        this.source = source;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("datacontenttype")
    public String getDatacontenttype() {
        return datacontenttype;
    }

    @JsonProperty("datacontenttype")
    public void setDatacontenttype(String datacontenttype) {
        this.datacontenttype = datacontenttype;
    }

    @JsonProperty("data")
    public Object getData() {
        return data;
    }

    @JsonProperty("data")
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

    @JsonProperty("time")
    public Instant getTime() {
        return time;
    }

    @JsonProperty("time")
    public void setTime(Instant time) {
        this.time = time;
    }
}
