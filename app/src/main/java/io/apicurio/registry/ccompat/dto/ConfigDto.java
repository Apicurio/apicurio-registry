package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Ales Justin
 */
public class ConfigDto {
    private String compatibilityLevel;

    @JsonProperty("compatibility")
    public String getCompatibilityLevel() {
        return this.compatibilityLevel;
    }

    @JsonProperty("compatibility")
    public void setCompatibilityLevel(String compatibilityLevel) {
        this.compatibilityLevel = compatibilityLevel;
    }
}
