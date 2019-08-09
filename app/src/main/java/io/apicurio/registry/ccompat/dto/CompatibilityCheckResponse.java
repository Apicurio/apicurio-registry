package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Ales Justin
 */
public class CompatibilityCheckResponse {

    private boolean isCompatible;

    @JsonProperty("is_compatible")
    public boolean getIsCompatible() {
        return isCompatible;
    }

    @JsonProperty("is_compatible")
    public void setIsCompatible(boolean isCompatible) {
        this.isCompatible = isCompatible;
    }
}
