package io.apicurio.registry.metrics;

/**
 * Metrics naming constants.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public interface MetricIDs {

    String REST_GROUP_TAG = "REST";

    String REST_REQUEST_RESPONSE_TIME = "rest_response_time";
    String REST_REQUEST_RESPONSE_TIME_DESC = "Time of a request-response interval.";

    String REST_REQUEST_COUNT = "rest_request_count";
    String REST_REQUEST_COUNT_DESC = "Total number of REST requests.";

    String REST_CONCURRENT_REQUEST_COUNT = "concurrent_request_count";
    String REST_CONCURRENT_REQUEST_COUNT_DESC = "Number of concurrent REST requests.";

    String STORAGE_GROUP_TAG = "STORAGE";

    String STORAGE_OPERATION_TIME = "storage_operation_time";
    String STORAGE_OPERATION_TIME_DESC = "Time for a artifactStore operation to process.";

    String STORAGE_OPERATION_COUNT = "storage_operation_count";
    String STORAGE_OPERATION_COUNT_DESC = "Total number of artifactStore operations.";

    String STORAGE_CONCURRENT_OPERATION_COUNT = "concurrent_operation_count";
    String STORAGE_CONCURRENT_OPERATION_COUNT_DESC = "Number of concurrent artifactStore operations.";
}
