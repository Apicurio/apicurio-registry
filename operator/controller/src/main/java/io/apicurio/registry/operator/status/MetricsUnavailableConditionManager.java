package io.apicurio.registry.operator.status;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_METRICS_UNAVAILABLE;

/**
 * Manages the condition that reports a failure to collect operand metrics.
 * <p>
 * Being unable to reach the operand is not an operator error, so it is reported separately and never fails
 * reconciliation. The condition is hidden while collection is working, and also while metrics collection is
 * disabled.
 */
public class MetricsUnavailableConditionManager extends AbstractConditionManager {

    private static final String REASON_AVAILABLE = "MetricsAvailable";

    private static final String REASON_COLLECTION_FAILED = "CollectionFailed";

    private String failure;

    MetricsUnavailableConditionManager() {
        resetCondition();
    }

    @Override
    void resetCondition() {
        current = new Condition();
        current.setType(TYPE_METRICS_UNAVAILABLE);
        failure = null;
    }

    /**
     * Record that the most recent collection attempt did not succeed.
     * <p>
     * This has to be called on every reconciliation while the failure persists, not only on the
     * reconciliation that first observed it, otherwise the condition would disappear and reappear between
     * scrape intervals.
     */
    public synchronized void recordFailure(String message) {
        this.failure = message;
    }

    @Override
    void updateCondition(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        if (failure != null) {
            current.setStatus(ConditionStatus.TRUE);
            current.setReason(REASON_COLLECTION_FAILED);
            current.setMessage("Could not collect metrics from the Apicurio Registry application: " + failure);
        } else {
            current.setStatus(ConditionStatus.FALSE);
            current.setReason(REASON_AVAILABLE);
            current.setMessage("Metrics were collected successfully.");
        }
    }
}
