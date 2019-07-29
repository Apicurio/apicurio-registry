package io.apicurio.registry.rest.dto;

import javax.json.bind.annotation.JsonbProperty;

/**
 * @author Ales Justin
 */
public class ConfigDto {
    private String compatibilityLevel;

    @JsonbProperty("compatibility")
    public String getCompatibilityLevel() {
        return this.compatibilityLevel;
    }

    @JsonbProperty("compatibility")
    public void setCompatibilityLevel(String compatibilityLevel) {
        this.compatibilityLevel = compatibilityLevel;
    }
}
