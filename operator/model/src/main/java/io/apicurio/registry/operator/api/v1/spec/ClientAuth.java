package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Controls whether the Registry HTTP server requires TLS client certificates (mTLS).
 * Maps to Quarkus {@code quarkus.http.ssl.client-auth}.
 */
public enum ClientAuth {

    @JsonProperty("none")
    NONE("none"),
    @JsonProperty("request")
    REQUEST("request"),
    @JsonProperty("required")
    REQUIRED("required");

    private final String value;

    ClientAuth(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
