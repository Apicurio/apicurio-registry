package io.apicurio.registry.metrics;

/**
 * Metrics naming constants.
 * <p>
 * See: - https://micrometer.io/docs/concepts#_naming_meters - https://prometheus.io/docs/practices/naming/
 * (Micrometer abstracts some naming aspects.)
 */
public interface MetricsConstants {

    // REST

    String REST_PREFIX = "rest.";
    String REST_REQUESTS = REST_PREFIX + "requests";
    String REST_REQUESTS_DESCRIPTION = "Timing and results of REST endpoints calls";

    // REST tags/labels

    String REST_REQUESTS_TAG_PATH = "path";
    String REST_REQUESTS_TAG_METHOD = "method";
    String REST_REQUESTS_TAG_STATUS_CODE_GROUP = "status.code.group";

    String VALUE_UNSPECIFIED = "(unspecified)";

    // Storage

    String STORAGE_PREFIX = "storage.";
    String STORAGE_METHOD_CALL = STORAGE_PREFIX + "method.call";
    String STORAGE_METHOD_CALL_DESCRIPTION = "Timing and results of storage methods calls";

    // Storage tags/labels

    String STORAGE_METHOD_CALL_TAG_METHOD = "method";
    String STORAGE_METHOD_CALL_TAG_SUCCESS = "success";
}
