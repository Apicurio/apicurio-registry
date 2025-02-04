package io.apicurio.registry.operator.status;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.util.HashSet;
import java.util.Set;

import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_VALIDATION_ERROR;

public class ValidationErrorConditionManager extends AbstractConditionManager {

    private static final String REASON_ERROR = "Error";
    private static final String REASON_NO_ERROR = "NoError";

    private final Set<String> errors = new HashSet<>();

    ValidationErrorConditionManager() {
        resetCondition();
    }

    @Override
    void resetCondition() {
        current = new Condition();
        current.setType(TYPE_VALIDATION_ERROR);
        errors.clear();
    }

    /**
     * Record a validation error as a format string.
     */
    public synchronized void recordError(String error, String... args) {
        errors.add(error.formatted(args));
    }

    /**
     * Used for testing.
     */
    public synchronized boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    void updateCondition(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        if (!errors.isEmpty()) {
            current.setStatus(ConditionStatus.TRUE);
            current.setReason(REASON_ERROR);
            var message = "Found " + errors.size() + " validation error(s):\n- ";
            message += String.join("\n- ", errors);
            current.setMessage(message);
        } else {
            current.setStatus(ConditionStatus.FALSE);
            current.setReason(REASON_NO_ERROR);
            current.setMessage("No error.");
        }
    }
}
