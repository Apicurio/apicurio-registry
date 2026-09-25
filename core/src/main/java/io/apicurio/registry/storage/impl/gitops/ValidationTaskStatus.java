package io.apicurio.registry.storage.impl.gitops;

/**
 * User-facing task states exposed through the REST API.
 */
public enum ValidationTaskStatus {

    PENDING("pending"),
    SUBMITTED("submitted"),
    FETCHING("fetching"),
    VALIDATING("validating"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    ValidationTaskStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
