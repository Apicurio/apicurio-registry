package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Ales Justin
 */
public class RegisterSchemaResponse {

    private long id;

    @JsonProperty("id")
    public long getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(long id) {
        this.id = id;
    }
}
