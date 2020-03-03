package io.apicurio.registry.cluster.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author Ales Justin
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthResponse {
    public static enum Status {
        UP,
        DOWN
    }

    @JsonProperty("status")
    public Status status;

    @JsonProperty("name")
    public String name;

    @JsonProperty("checks")
    public List<HealthResponse> checks;
}
