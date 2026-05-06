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

    // Iceberg

    String ICEBERG_PREFIX = "iceberg.";
    String ICEBERG_NAMESPACE_OPERATIONS = ICEBERG_PREFIX + "namespace.operations";
    String ICEBERG_NAMESPACE_OPERATIONS_DESCRIPTION = "Iceberg namespace lifecycle operations";
    String ICEBERG_TABLE_OPERATIONS = ICEBERG_PREFIX + "table.operations";
    String ICEBERG_TABLE_OPERATIONS_DESCRIPTION = "Iceberg table lifecycle operations";
    String ICEBERG_VIEW_OPERATIONS = ICEBERG_PREFIX + "view.operations";
    String ICEBERG_VIEW_OPERATIONS_DESCRIPTION = "Iceberg view lifecycle operations";
    String ICEBERG_COMMIT_CONFLICTS = ICEBERG_PREFIX + "commit.conflicts";
    String ICEBERG_COMMIT_CONFLICTS_DESCRIPTION = "Iceberg commit conflicts due to optimistic concurrency";
    String ICEBERG_COMMIT_DURATION = ICEBERG_PREFIX + "commit.duration";
    String ICEBERG_COMMIT_DURATION_DESCRIPTION = "Duration of Iceberg commit operations";
    String ICEBERG_ERRORS = ICEBERG_PREFIX + "errors";
    String ICEBERG_ERRORS_DESCRIPTION = "Iceberg-specific errors by type";

    // Iceberg tags/labels

    String ICEBERG_TAG_OPERATION = "operation";
    String ICEBERG_TAG_RESULT = "result";
    String ICEBERG_TAG_ENTITY_TYPE = "entity_type";
    String ICEBERG_TAG_ERROR_TYPE = "error_type";
}
