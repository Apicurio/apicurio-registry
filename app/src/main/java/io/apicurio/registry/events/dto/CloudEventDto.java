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
import org.json.JSONArray;
import org.json.JSONObject;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * Normalizes the event payload for Jackson serialization.
     * <p>
     * If a legacy {@link JSONObject} is passed (e.g. from a custom event source), it is
     * converted to a plain {@link java.util.Map} so Jackson can serialize it correctly.
     * Modern registry events return typed DTOs or {@code Map<String, Object>} directly,
     * which Jackson handles without conversion.
     */
    private static Object normalizeData(Object data) {
        if (data == null || isScalar(data)) {
            return data;
        }
        if (data instanceof JSONObject jsonObject) {
            return normalizeJsonObject(jsonObject);
        }
        if (data instanceof JSONArray jsonArray) {
            return normalizeJsonArray(jsonArray);
        }
        if (data instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (data.getClass().isArray()) {
            return normalizeArray(data);
        }
        if (data instanceof Iterable<?> iterable) {
            return normalizeIterable(iterable);
        }
        if (isJavaPlatformType(data)) {
            return data;
        }

        return normalizeBean(data);
    }

    private static boolean isScalar(Object data) {
        return data instanceof String || data instanceof Number || data instanceof Boolean
                || data instanceof Character || data instanceof Enum<?> || data instanceof Instant;
    }

    private static Map<String, Object> normalizeJsonObject(JSONObject jsonObject) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (String key : jsonObject.keySet()) {
            normalized.put(key, normalizeData(jsonObject.get(key)));
        }
        return normalized;
    }

    private static List<Object> normalizeJsonArray(JSONArray jsonArray) {
        List<Object> normalized = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            normalized.add(normalizeData(jsonArray.get(i)));
        }
        return normalized;
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> map) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), normalizeData(entry.getValue()));
        }
        return normalized;
    }

    private static List<Object> normalizeArray(Object data) {
        List<Object> normalized = new ArrayList<>();
        for (int i = 0; i < Array.getLength(data); i++) {
            normalized.add(normalizeData(Array.get(data, i)));
        }
        return normalized;
    }

    private static List<Object> normalizeIterable(Iterable<?> iterable) {
        List<Object> normalized = new ArrayList<>();
        for (Object item : iterable) {
            normalized.add(normalizeData(item));
        }
        return normalized;
    }

    private static boolean isJavaPlatformType(Object data) {
        return data.getClass().getPackageName().startsWith("java.");
    }

    private static Object normalizeBean(Object data) {

        try {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (PropertyDescriptor pd : Introspector.getBeanInfo(data.getClass()).getPropertyDescriptors()) {
                if (pd.getReadMethod() == null || "class".equals(pd.getName())) {
                    continue;
                }
                Object value = pd.getReadMethod().invoke(data);
                normalized.put(pd.getName(), normalizeData(value));
            }
            return normalized;
        } catch (ReflectiveOperationException | IntrospectionException e) {
            return data;
        }
    }

    private static Object addEventEnvelope(OutboxEvent event, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Object normalized = normalizeData(data);
        if (normalized instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                payload.put(String.valueOf(entry.getKey()), normalizeData(entry.getValue()));
            }
        } else if (normalized != null) {
            payload.put("value", normalized);
        }
        payload.put("eventType", event.getType());
        payload.put("id", event.getId());
        return payload;
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
                .withData(addEventEnvelope(event, event.getPayload()));
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
