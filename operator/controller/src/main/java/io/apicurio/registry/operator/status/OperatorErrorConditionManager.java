package io.apicurio.registry.operator.status;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_OPERATOR_ERROR;

/**
 * Manages the condition that reports unexpected operator errors.
 */
public class OperatorErrorConditionManager extends AbstractConditionManager {

    private static final Logger log = LoggerFactory.getLogger(OperatorErrorConditionManager.class);

    private static final String REASON_NO_ERROR = "NoError";

    private final List<Exception> exceptions = new ArrayList<>();

    OperatorErrorConditionManager() {
        resetCondition();
    }

    @Override
    void resetCondition() {
        current = new Condition();
        current.setType(TYPE_OPERATOR_ERROR);
        exceptions.clear();
    }

    /**
     * Record an operator exception.
     */
    public synchronized void recordException(Exception exception) {
        Stream.of(exception)
                .flatMap(e -> {
                    if (e instanceof AggregatedOperatorException ex) {
                        // Try to unwrap AggregatedOperatorException
                        return ex.getAggregatedExceptions().values().stream();
                    } else {
                        return Stream.of(e);
                    }
                })
                .filter(e -> {
                    if (e instanceof KubernetesClientException ex) {
                        if ("Conflict".equals(ex.getStatus().getReason())) {
                            log.debug("Ignoring exception.", ex);
                            return false;
                        }
                    }
                    return true;
                }).forEach(this.exceptions::add);
    }

    @Override
    void updateCondition(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        if (!exceptions.isEmpty()) {
            current.setStatus(ConditionStatus.TRUE);
            current.setReason(exceptions.size() == 1 ? exceptions.get(0).getClass().getSimpleName() : "AggregatedOperatorException");
            StringBuilder message = new StringBuilder("Encountered " + exceptions.size() + " operator error(s):");
            for (var ex : exceptions) {
                var m = "\n- " + ex.getClass().getSimpleName() + ": " + (ex.getMessage() != null ? ex.getMessage() : "<Details not available>");
                if (ex.getCause() != null && ex.getCause().getMessage() != null && !ex.getClass().equals(ex.getCause().getClass())) {
                    m += "\n    Caused by " + ex.getCause().getClass().getSimpleName() + ": " + ex.getCause().getMessage();
                }
                message.append(m);
            }
            current.setMessage(message.toString());
        } else {
            current.setStatus(ConditionStatus.FALSE);
            current.setReason(REASON_NO_ERROR);
            current.setMessage("No error.");
        }
    }
}
