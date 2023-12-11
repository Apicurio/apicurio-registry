package io.apicurio.registry.support;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CitizenIdentifier {

    @JsonProperty("identifier")
    private Integer identifier;

    public CitizenIdentifier() {
    }

    public CitizenIdentifier(Integer identifier) {
        this.identifier = identifier;
    }

    public Integer getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Integer identifier) {
        this.identifier = identifier;
    }
}
