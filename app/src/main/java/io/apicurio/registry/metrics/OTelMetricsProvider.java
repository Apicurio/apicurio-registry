package io.apicurio.registry.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Provider for OpenTelemetry metrics in Apicurio Registry.
 * This class provides custom metrics using the OpenTelemetry API directly,
 * complementing the existing Micrometer-based metrics.
 *
 * When OpenTelemetry is disabled, the GlobalOpenTelemetry.getMeter() returns
 * a no-op meter that has minimal overhead.
 */
@ApplicationScoped
public class OTelMetricsProvider {

    private static final String INSTRUMENTATION_NAME = "io.apicurio.registry";
    private static final String METRIC_PREFIX = "apicurio.";

    private static final AttributeKey<String> GROUP_ID_KEY = AttributeKey.stringKey("groupId");
    private static final AttributeKey<String> ARTIFACT_TYPE_KEY = AttributeKey.stringKey("artifactType");
    private static final AttributeKey<String> OPERATION_KEY = AttributeKey.stringKey("operation");
    private static final AttributeKey<String> RESULT_KEY = AttributeKey.stringKey("result");

    private LongCounter artifactCreatedCounter;
    private LongCounter artifactDeletedCounter;
    private LongCounter versionCreatedCounter;
    private LongCounter schemaValidationCounter;
    private LongCounter ruleEvaluationCounter;
    private LongCounter searchRequestCounter;

    // For testing: allows injecting a custom Meter
    private Meter testMeter;

    /**
     * Default constructor for CDI.
     */
    public OTelMetricsProvider() {
    }

    /**
     * Constructor for testing with a custom Meter.
     * This allows testing without GlobalOpenTelemetry which can cause classloader issues.
     *
     * @param meter the Meter to use for creating counters
     */
    OTelMetricsProvider(Meter meter) {
        this.testMeter = meter;
    }

    @PostConstruct
    public void init() {
        Meter meter = testMeter != null ? testMeter : GlobalOpenTelemetry.getMeter(INSTRUMENTATION_NAME);

        artifactCreatedCounter = meter.counterBuilder(METRIC_PREFIX + "artifacts.created")
                .setDescription("Total number of artifacts created")
                .setUnit("1")
                .build();

        artifactDeletedCounter = meter.counterBuilder(METRIC_PREFIX + "artifacts.deleted")
                .setDescription("Total number of artifacts deleted")
                .setUnit("1")
                .build();

        versionCreatedCounter = meter.counterBuilder(METRIC_PREFIX + "versions.created")
                .setDescription("Total number of artifact versions created")
                .setUnit("1")
                .build();

        schemaValidationCounter = meter.counterBuilder(METRIC_PREFIX + "schema.validations")
                .setDescription("Total number of schema validations performed")
                .setUnit("1")
                .build();

        ruleEvaluationCounter = meter.counterBuilder(METRIC_PREFIX + "rules.evaluated")
                .setDescription("Total number of rule evaluations performed")
                .setUnit("1")
                .build();

        searchRequestCounter = meter.counterBuilder(METRIC_PREFIX + "search.requests")
                .setDescription("Total number of search requests")
                .setUnit("1")
                .build();
    }

    /**
     * Record that an artifact was created.
     *
     * @param groupId the group ID of the artifact
     * @param artifactType the type of the artifact
     */
    public void recordArtifactCreated(String groupId, String artifactType) {
        artifactCreatedCounter.add(1, Attributes.of(
                GROUP_ID_KEY, groupId != null ? groupId : "default",
                ARTIFACT_TYPE_KEY, artifactType != null ? artifactType : "unknown"
        ));
    }

    /**
     * Record that an artifact was deleted.
     *
     * @param groupId the group ID of the artifact
     * @param artifactType the type of the artifact
     */
    public void recordArtifactDeleted(String groupId, String artifactType) {
        artifactDeletedCounter.add(1, Attributes.of(
                GROUP_ID_KEY, groupId != null ? groupId : "default",
                ARTIFACT_TYPE_KEY, artifactType != null ? artifactType : "unknown"
        ));
    }

    /**
     * Record that an artifact version was created.
     *
     * @param groupId the group ID of the artifact
     * @param artifactType the type of the artifact
     */
    public void recordVersionCreated(String groupId, String artifactType) {
        versionCreatedCounter.add(1, Attributes.of(
                GROUP_ID_KEY, groupId != null ? groupId : "default",
                ARTIFACT_TYPE_KEY, artifactType != null ? artifactType : "unknown"
        ));
    }

    /**
     * Record a schema validation operation.
     *
     * @param artifactType the type of the schema
     * @param success whether the validation was successful
     */
    public void recordSchemaValidation(String artifactType, boolean success) {
        schemaValidationCounter.add(1, Attributes.of(
                ARTIFACT_TYPE_KEY, artifactType != null ? artifactType : "unknown",
                RESULT_KEY, success ? "success" : "failure"
        ));
    }

    /**
     * Record a rule evaluation.
     *
     * @param operation the type of operation being validated
     * @param success whether the rule evaluation passed
     */
    public void recordRuleEvaluation(String operation, boolean success) {
        ruleEvaluationCounter.add(1, Attributes.of(
                OPERATION_KEY, operation != null ? operation : "unknown",
                RESULT_KEY, success ? "success" : "failure"
        ));
    }

    /**
     * Record a search request.
     *
     * @param searchType the type of search (e.g., "artifacts", "versions", "groups")
     */
    public void recordSearchRequest(String searchType) {
        searchRequestCounter.add(1, Attributes.of(
                OPERATION_KEY, searchType != null ? searchType : "unknown"
        ));
    }
}
