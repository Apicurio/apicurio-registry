package io.apicurio.registry.metrics;

import io.apicurio.registry.observability.OTelAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(OTelMetricsProvider.class);

    private static final String INSTRUMENTATION_NAME = "io.apicurio.registry";
    private static final String METRIC_PREFIX = "apicurio.";

    private static final String ICEBERG_PREFIX = METRIC_PREFIX + "iceberg.";

    private static final String DEFAULT_GROUP = "default";
    private static final String UNKNOWN_TYPE = "unknown";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAILURE = "failure";

    private LongCounter artifactCreatedCounter;
    private LongCounter artifactDeletedCounter;
    private LongCounter versionCreatedCounter;
    private LongCounter schemaValidationCounter;
    private LongCounter ruleEvaluationCounter;
    private LongCounter searchRequestCounter;

    // Usage telemetry
    private LongUpDownCounter activeSchemasGauge;
    private LongUpDownCounter staleSchemasGauge;
    private LongUpDownCounter deadSchemasGauge;
    private long lastActive;
    private long lastStale;
    private long lastDead;

    // Iceberg counters
    private LongCounter icebergNamespaceOpsCounter;
    private LongCounter icebergTableOpsCounter;
    private LongCounter icebergViewOpsCounter;
    private LongCounter icebergCommitConflictsCounter;
    private LongCounter icebergErrorsCounter;

    @PostConstruct
    public void init() {
        Meter meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_NAME);

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

        // Usage telemetry
        activeSchemasGauge = meter.upDownCounterBuilder(METRIC_PREFIX + MetricsConstants.USAGE_SCHEMAS_ACTIVE)
                .setDescription(MetricsConstants.USAGE_SCHEMAS_ACTIVE_DESCRIPTION)
                .setUnit("1")
                .build();

        staleSchemasGauge = meter.upDownCounterBuilder(METRIC_PREFIX + MetricsConstants.USAGE_SCHEMAS_STALE)
                .setDescription(MetricsConstants.USAGE_SCHEMAS_STALE_DESCRIPTION)
                .setUnit("1")
                .build();

        deadSchemasGauge = meter.upDownCounterBuilder(METRIC_PREFIX + MetricsConstants.USAGE_SCHEMAS_DEAD)
                .setDescription(MetricsConstants.USAGE_SCHEMAS_DEAD_DESCRIPTION)
                .setUnit("1")
                .build();

        // Iceberg counters
        icebergNamespaceOpsCounter = meter.counterBuilder(ICEBERG_PREFIX + "namespace.operations")
                .setDescription("Iceberg namespace lifecycle operations")
                .setUnit("1")
                .build();

        icebergTableOpsCounter = meter.counterBuilder(ICEBERG_PREFIX + "table.operations")
                .setDescription("Iceberg table lifecycle operations")
                .setUnit("1")
                .build();

        icebergViewOpsCounter = meter.counterBuilder(ICEBERG_PREFIX + "view.operations")
                .setDescription("Iceberg view lifecycle operations")
                .setUnit("1")
                .build();

        icebergCommitConflictsCounter = meter.counterBuilder(ICEBERG_PREFIX + "commit.conflicts")
                .setDescription("Iceberg commit conflicts due to optimistic concurrency")
                .setUnit("1")
                .build();

        icebergErrorsCounter = meter.counterBuilder(ICEBERG_PREFIX + "errors")
                .setDescription("Iceberg-specific errors by type")
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
        safeIncrement(artifactCreatedCounter, Attributes.of(
                OTelAttributes.ATTR_GROUP_ID, groupId != null ? groupId : DEFAULT_GROUP,
                OTelAttributes.ATTR_ARTIFACT_TYPE, artifactType != null ? artifactType : UNKNOWN_TYPE
        ));
    }

    /**
     * Record that an artifact was deleted.
     *
     * @param groupId the group ID of the artifact
     * @param artifactType the type of the artifact
     */
    public void recordArtifactDeleted(String groupId, String artifactType) {
        safeIncrement(artifactDeletedCounter, Attributes.of(
                OTelAttributes.ATTR_GROUP_ID, groupId != null ? groupId : DEFAULT_GROUP,
                OTelAttributes.ATTR_ARTIFACT_TYPE, artifactType != null ? artifactType : UNKNOWN_TYPE
        ));
    }

    /**
     * Record that an artifact version was created.
     *
     * @param groupId the group ID of the artifact
     * @param artifactType the type of the artifact
     */
    public void recordVersionCreated(String groupId, String artifactType) {
        safeIncrement(versionCreatedCounter, Attributes.of(
                OTelAttributes.ATTR_GROUP_ID, groupId != null ? groupId : DEFAULT_GROUP,
                OTelAttributes.ATTR_ARTIFACT_TYPE, artifactType != null ? artifactType : UNKNOWN_TYPE
        ));
    }

    /**
     * Record a schema validation operation.
     *
     * @param artifactType the type of the schema
     * @param success whether the validation was successful
     */
    public void recordSchemaValidation(String artifactType, boolean success) {
        safeIncrement(schemaValidationCounter, Attributes.of(
                OTelAttributes.ATTR_ARTIFACT_TYPE, artifactType != null ? artifactType : UNKNOWN_TYPE,
                OTelAttributes.ATTR_RESULT, success ? RESULT_SUCCESS : RESULT_FAILURE
        ));
    }

    /**
     * Record a rule evaluation.
     *
     * @param operation the type of operation being validated
     * @param success whether the rule evaluation passed
     */
    public void recordRuleEvaluation(String operation, boolean success) {
        safeIncrement(ruleEvaluationCounter, Attributes.of(
                OTelAttributes.ATTR_OPERATION, operation != null ? operation : UNKNOWN_TYPE,
                OTelAttributes.ATTR_RESULT, success ? RESULT_SUCCESS : RESULT_FAILURE
        ));
    }

    /**
     * Record a search request.
     *
     * @param searchType the type of search (e.g., "artifacts", "versions", "groups")
     */
    public void recordSearchRequest(String searchType) {
        safeIncrement(searchRequestCounter, Attributes.of(
                OTelAttributes.ATTR_OPERATION, searchType != null ? searchType : UNKNOWN_TYPE
        ));
    }

    /**
     * Record an Iceberg namespace operation.
     *
     * @param operation the operation type (e.g., "created", "deleted", "updated")
     */
    public void recordIcebergNamespaceOperation(String operation) {
        safeIncrement(icebergNamespaceOpsCounter, Attributes.of(
                OTelAttributes.ATTR_OPERATION, operation,
                OTelAttributes.ATTR_RESULT, RESULT_SUCCESS
        ));
    }

    /**
     * Record an Iceberg table operation.
     *
     * @param operation the operation type (e.g., "created", "deleted", "renamed", "committed")
     */
    public void recordIcebergTableOperation(String operation) {
        safeIncrement(icebergTableOpsCounter, Attributes.of(
                OTelAttributes.ATTR_OPERATION, operation,
                OTelAttributes.ATTR_RESULT, RESULT_SUCCESS
        ));
    }

    /**
     * Record an Iceberg view operation.
     *
     * @param operation the operation type (e.g., "created", "deleted", "renamed", "replaced")
     */
    public void recordIcebergViewOperation(String operation) {
        safeIncrement(icebergViewOpsCounter, Attributes.of(
                OTelAttributes.ATTR_OPERATION, operation,
                OTelAttributes.ATTR_RESULT, RESULT_SUCCESS
        ));
    }

    /**
     * Record an Iceberg commit conflict.
     *
     * @param entityType the entity type ("table" or "view")
     */
    public void recordIcebergCommitConflict(String entityType) {
        safeIncrement(icebergCommitConflictsCounter, Attributes.of(
                OTelAttributes.ATTR_ENTITY_TYPE, entityType
        ));
    }

    /**
     * Record an Iceberg-specific error.
     *
     * @param errorType the Iceberg error type (e.g., "NoSuchNamespaceException")
     */
    public void recordIcebergError(String errorType) {
        safeIncrement(icebergErrorsCounter, Attributes.of(
                OTelAttributes.ATTR_ERROR_TYPE, errorType
        ));
    }

    private void safeIncrement(LongCounter counter, Attributes attributes) {
        try {
            counter.add(1, attributes);
        } catch (Exception e) {
            log.debug("Failed to record OTel metric", e);
        }
    }

    public synchronized void updateUsageSummaryCounts(int active, int stale, int dead) {
        try {
            activeSchemasGauge.add(active - lastActive);
            staleSchemasGauge.add(stale - lastStale);
            deadSchemasGauge.add(dead - lastDead);
        } catch (Exception e) {
            log.debug("Failed to record OTel usage summary metrics", e);
        } finally {
            lastActive = active;
            lastStale = stale;
            lastDead = dead;
        }
    }
}
