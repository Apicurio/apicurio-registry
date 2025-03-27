
package io.apicurio.registry.config.artifactTypes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type",
        "url",
        "method",
        "headers",
        "payload"
})
@io.quarkus.runtime.annotations.RegisterForReflection
public class WebhookProvider extends Provider {

    /**
     * (Required)
     */
    @JsonProperty("type")
    private String type;
    /**
     * (Required)
     */
    @JsonProperty("url")
    private String url;
    /**
     * (Required)
     */
    @JsonProperty("method")
    private String method;
    /**
     * Root Type for WebhookHeaders
     * <p>
     */
    @JsonProperty("headers")
    @JsonPropertyDescription("")
    private Map<String, String> headers;
    /**
     * Root Type for WebhookPayload
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("payload")
    @JsonPropertyDescription("")
    private Map<String, Object> payload;

    /**
     * (Required)
     */
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     * (Required)
     */
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    /**
     * (Required)
     */
    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    /**
     * (Required)
     */
    @JsonProperty("url")
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * (Required)
     */
    @JsonProperty("method")
    public String getMethod() {
        return method;
    }

    /**
     * (Required)
     */
    @JsonProperty("method")
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * Root Type for WebhookHeaders
     * <p>
     */
    @JsonProperty("headers")
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Root Type for WebhookHeaders
     * <p>
     */
    @JsonProperty("headers")
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /**
     * Root Type for WebhookPayload
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("payload")
    public Map<String, Object> getPayload() {
        return payload;
    }

    /**
     * Root Type for WebhookPayload
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("payload")
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

}
