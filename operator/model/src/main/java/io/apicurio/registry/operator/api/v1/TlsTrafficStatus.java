package io.apicurio.registry.operator.api.v1;

public enum TlsTrafficStatus {

    ENABLED("enabled"),
    DISABLED("disabled"),
    REDIRECT("redirect");

    private final String value;

    TlsTrafficStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
