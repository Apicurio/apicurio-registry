package io.apicurio.multitenant.metrics;

/**
 * Metrics naming constants.
 * <p>
 * See:
 * - https://micrometer.io/docs/concepts#_naming_meters
 * - https://prometheus.io/docs/practices/naming/ (Micrometer abstracts some naming aspects.)
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public interface MetricsConstants {

    // REST

    String REST_PREFIX = "rest.";
    String REST_REQUESTS = REST_PREFIX + "requests";
    String REST_REQUESTS_TIMER_DESCRIPTION = "Timing and results of REST endpoints calls";

    String REST_REQUESTS_COUNTER = REST_REQUESTS + ".count";
    String REST_REQUESTS_COUNTER_DESCRIPTION = "Count and results of REST endpoints calls";

    // REST tags/labels

    String REST_REQUESTS_TAG_PATH = "path";
    String REST_REQUESTS_TAG_METHOD = "method";
    String REST_REQUESTS_TAG_STATUS_CODE_FAMILY = "status_code_group";

    // usage metrics
    String USAGE_TENANTS = "usage.tenants";
    String USAGE_DELETING_TENANTS = "usage.deleting.tenants";

    String TAG_USAGE_TENANTS_STATUS = "status";

}
