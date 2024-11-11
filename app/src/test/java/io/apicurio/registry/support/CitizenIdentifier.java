package io.apicurio.registry.support;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CitizenIdentifier {

    @JsonProperty("identifier")
    private Integer identifier;

    @JsonProperty("qualification")
    private IdentifierQualification identifierQualification;

    public CitizenIdentifier() {
    }

    public CitizenIdentifier(Integer identifier) {
        this.identifier = identifier;
    }

    public CitizenIdentifier(Integer identifier, IdentifierQualification identifierQualification) {
        this.identifier = identifier;
        this.identifierQualification = identifierQualification;
    }

    public Integer getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Integer identifier) {
        this.identifier = identifier;
    }

    public IdentifierQualification getIdentifierQualification() {
        return identifierQualification;
    }

    public void setIdentifierQualification(IdentifierQualification identifierQualification) {
        this.identifierQualification = identifierQualification;
    }
}
