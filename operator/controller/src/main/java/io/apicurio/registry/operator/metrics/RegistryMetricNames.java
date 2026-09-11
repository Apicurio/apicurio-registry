package io.apicurio.registry.operator.metrics;

/**
 * Names of the operand metrics that the operator reads.
 * <p>
 * These are the Prometheus names, i.e. the Micrometer meter names with dots replaced by underscores and,
 * for timers, the base unit appended. They are collected here so that a change in the operand only requires
 * a change in one place.
 */
public final class RegistryMetricNames {

    /**
     * Counter component of the {@code rest.requests} timer registered by the Registry REST metrics filter.
     * Carries the {@link #TAG_STATUS_CODE_GROUP} tag.
     */
    public static final String REST_REQUESTS_COUNT = "rest_requests_seconds_count";

    /**
     * Response status code group, e.g. {@code 2xx} or {@code 5xx}.
     */
    public static final String TAG_STATUS_CODE_GROUP = "status_code_group";

    /**
     * Status code group covering server-side failures.
     */
    public static final String STATUS_CODE_GROUP_SERVER_ERROR = "5xx";

    /**
     * Whether a value of the {@link #TAG_STATUS_CODE_GROUP} tag denotes a server-side failure.
     * <p>
     * The Registry normally reports whole groups such as {@code 5xx}, but
     * {@code apicurio.metrics.rest.explicit-status-codes-list} promotes individual status codes to a group of
     * their own. It defaults to {@code 401}, and an operator who added a 5xx code to it would otherwise see
     * those responses silently drop out of the reported error rate.
     */
    public static boolean isServerError(String statusCodeGroup) {
        if (statusCodeGroup == null || statusCodeGroup.isEmpty() || statusCodeGroup.charAt(0) != '5') {
            return false;
        }
        if (STATUS_CODE_GROUP_SERVER_ERROR.equals(statusCodeGroup)) {
            return true;
        }
        if (statusCodeGroup.length() != 3) {
            return false;
        }
        return Character.isDigit(statusCodeGroup.charAt(1)) && Character.isDigit(statusCodeGroup.charAt(2));
    }

    /**
     * Number of database connections currently handed out by the Agroal pool.
     */
    public static final String POOL_ACTIVE_COUNT = "agroal_active_count";

    /**
     * Number of database connections currently idle in the Agroal pool.
     */
    public static final String POOL_AVAILABLE_COUNT = "agroal_available_count";

    /**
     * Number of artifacts currently held in storage.
     */
    public static final String STORAGE_ARTIFACTS = "storage_artifacts";

    /**
     * Number of artifact versions currently held in storage.
     */
    public static final String STORAGE_ARTIFACT_VERSIONS = "storage_artifact_versions";

    /**
     * Highest consumer lag, in records, across the partitions assigned to the KafkaSQL journal consumer.
     * Present only when KafkaSQL storage is in use.
     */
    public static final String KAFKA_RECORDS_LAG_MAX = "kafka_consumer_fetch_manager_records_lag_max";

    private RegistryMetricNames() {
    }
}
