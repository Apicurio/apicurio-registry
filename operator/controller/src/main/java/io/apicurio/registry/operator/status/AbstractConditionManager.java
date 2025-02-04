package io.apicurio.registry.operator.status;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.time.Instant;

public abstract class AbstractConditionManager {

    private Condition previous;

    Condition current;

    abstract void resetCondition();

    abstract void updateCondition(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context);

    boolean show() {
        return ConditionStatus.TRUE.equals(current.getStatus());
    }

    Condition getCondition() {
        var now = Instant.now();
        if (previous == null || !previous.getStatus().equals(current.getStatus())) {
            current.setLastTransitionTime(now);
        } else {
            current.setLastTransitionTime(previous.getLastTransitionTime());
        }
        if (previous == null || !Condition.isEquivalent(previous, current)) {
            current.setLastUpdateTime(now);
        } else {
            current.setLastUpdateTime(previous.getLastUpdateTime());
        }
        previous = current;
        resetCondition();
        return previous;
    }
}
