package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Ales Justin
 */
public class ModeDto {

    private String mode;

    @JsonProperty("mode")
    public String getMode() {
        return this.mode;
    }

    @JsonProperty("mode")
    public void setMode(String mode) {
        this.mode = mode;
    }

}
