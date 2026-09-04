package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

/**
 * CloudEvents 1.0 specification-compliant envelope.
 * <p>
 * See <a href="https://github.com/cloudevents/spec/blob/v1.0.0/cloudevents/spec.md">the CloudEvents 1.0
 * spec</a> for the attribute definitions. {@code id}, {@code source}, {@code type} and
 * {@code specversion} are required; {@code subject}, {@code datacontenttype} and {@code time} are
 * optional.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "specversion", "id", "source", "type", "subject", "datacontenttype", "data", "time" })
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

    @JsonProperty("subject")
    private String subject;

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

    public CloudEventDto withSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public CloudEventDto withDatacontenttype(String datacontenttype) {
        this.datacontenttype = datacontenttype;
        return this;
    }

    public CloudEventDto withData(Object data) {
        this.data = data;
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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
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
        this.data = data;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    /**
     * Validates that this event carries all CloudEvents 1.0 required attributes ({@code id},
     * {@code source}, {@code type}), so callers cannot emit a spec-invalid event whose required
     * fields silently disappear from the wire output.
     *
     * @throws IllegalArgumentException if any required attribute is null/blank
     */
    public void validate() {
        if (isBlank(specversion)) {
            throw new IllegalArgumentException("CloudEvent 'specversion' is a required attribute and must not be blank");
        }
        if (isBlank(id)) {
            throw new IllegalArgumentException("CloudEvent 'id' is a required attribute and must not be blank");
        }
        if (isBlank(source)) {
            throw new IllegalArgumentException("CloudEvent 'source' is a required attribute and must not be blank");
        }
        if (isBlank(type)) {
            throw new IllegalArgumentException("CloudEvent 'type' is a required attribute and must not be blank");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
