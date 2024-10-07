package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Status;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.apicurio.registry.operator.api.v1.status.Conditions;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StatusUpdater {

    public static final String ERROR_TYPE = "ERROR";

    private ApicurioRegistry3 registry;

    public StatusUpdater(ApicurioRegistry3 registry) {
        this.registry = registry;
    }

    public ApicurioRegistry3Status errorStatus(Exception e) {
        Instant lastTransitionTime = Instant.now();
        if (registry != null && registry.getStatus() != null
                && registry.getStatus().getConditions().size() > 0 &&
                // TODO: better `lastTransitionTime` handling
                registry.getStatus().getConditions().get(0).getLastTransitionTime() != null) {
            lastTransitionTime = registry.getStatus().getConditions().get(0).getLastTransitionTime();
        }

        var generation = registry.getMetadata() == null ? null : registry.getMetadata().getGeneration();
        var newLastTransitionTime = Instant.now();
        var errorCondition = new Conditions();
        errorCondition.setStatus(ConditionStatus.TRUE);
        errorCondition.setType(ERROR_TYPE);
        errorCondition.setObservedGeneration(generation);
        errorCondition.setLastTransitionTime(newLastTransitionTime);
        errorCondition.setMessage(
                Arrays.stream(e.getStackTrace()).map(st -> st.toString()).collect(Collectors.joining("\n")));
        errorCondition.setReason("reasons");

        var status = new ApicurioRegistry3Status();
        status.setConditions(List.of(errorCondition));

        return status;
    }

    public void next(ApicurioRegistry3Status status, Deployment deployment) {
        var lastTransitionTime = Instant.now();
        if (registry != null && registry.getStatus() != null
                && registry.getStatus().getConditions().size() > 0 &&
                // TODO: should we sort the conditions before taking the first?
                registry.getStatus().getConditions().get(0).getLastTransitionTime() != null) {
            lastTransitionTime = registry.getStatus().getConditions().get(0).getLastTransitionTime();
        }

        var generation = registry.getMetadata() == null ? null : registry.getMetadata().getGeneration();
        var nextCondition = new Conditions();
        nextCondition.setStatus(ConditionStatus.TRUE);
        nextCondition.setType(ERROR_TYPE);
        nextCondition.setObservedGeneration(generation);
        nextCondition.setLastTransitionTime(lastTransitionTime);
        nextCondition.setMessage("TODO");
        nextCondition.setReason("reasons");

        status.setConditions(List.of(nextCondition));
    }
}
