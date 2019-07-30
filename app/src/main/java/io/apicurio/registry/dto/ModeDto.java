package io.apicurio.registry.dto;

import javax.json.bind.annotation.JsonbProperty;

/**
 * @author Ales Justin
 */
public class ModeDto {

    private String mode;

    @JsonbProperty("mode")
    public String getMode() {
        return this.mode;
    }

    @JsonbProperty("mode")
    public void setMode(String mode) {
        this.mode = mode;
    }

}
