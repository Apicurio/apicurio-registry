package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Status;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(StatusUpdater.class);

    public static final String ERROR_TYPE = "ERROR";
    public static final String READY_TYPE = "READY";
    public static final String STARTED_TYPE = "STARTED";
    public static final String UNKNOWN_TYPE = "UNKNOWN";

    private ApicurioRegistry3 registry;

    public StatusUpdater(ApicurioRegistry3 registry) {
        this.registry = registry;
    }

    private Condition defaultCondition() {
        var condition = new Condition();
        condition.setStatus(ConditionStatus.TRUE);
        condition.setObservedGeneration(
                registry.getMetadata() == null ? null : registry.getMetadata().getGeneration());
        condition.setLastTransitionTime(Instant.now());
        return condition;
    }

    public ApicurioRegistry3Status errorStatus(Exception e) {
        var errorCondition = defaultCondition();
        errorCondition.setType(ERROR_TYPE);
        errorCondition.setMessage(
                Arrays.stream(e.getStackTrace()).map(st -> st.toString()).collect(Collectors.joining("\n")));
        errorCondition.setReason("ERROR_STATUS");

        var status = new ApicurioRegistry3Status();
        status.setConditions(List.of(errorCondition));

        return status;
    }

    public ApicurioRegistry3Status next(Deployment deployment) {
        log.debug("Setting status based on Deployment: " + deployment);
        if (deployment != null && deployment.getStatus() != null) {
            if (deployment.getStatus().getConditions().stream()
                    .anyMatch(condition -> condition.getStatus().equalsIgnoreCase(
                            ConditionStatus.TRUE.getValue()) && condition.getType().equals("Available"))
                    && !registry.withStatus().getConditions().stream()
                            .anyMatch(condition -> condition.getType().equals(READY_TYPE))) {
                var readyCondition = defaultCondition();
                readyCondition.setType(READY_TYPE);
                readyCondition.setMessage("Deployment is available");
                readyCondition.setReason("READY_STATUS");
                var conditions = registry.getStatus().getConditions();
                conditions.add(readyCondition);
                return registry.getStatus();
            } else if (deployment.getStatus().getConditions().size() > 0
                    && !registry.withStatus().getConditions().stream()
                            .anyMatch(condition -> condition.getStatus().getValue().equals(STARTED_TYPE))) {
                var generation = registry.getMetadata() == null ? null
                    : registry.getMetadata().getGeneration();
                var nextCondition = defaultCondition();
                nextCondition.setType(STARTED_TYPE);
                nextCondition.setMessage("Deployment conditions:\n" + deployment.getStatus().getConditions()
                        .stream().map(dc -> dc.getType()).collect(Collectors.joining("\n")));
                nextCondition.setReason("DEPLOYMENT_STARTED");

                var status = new ApicurioRegistry3Status();
                status.setConditions(List.of(nextCondition));

                return status;
            } else {
                var nextCondition = defaultCondition();
                nextCondition.setType(UNKNOWN_TYPE);
                nextCondition.setMessage("Deployment conditions:\n" + deployment.getStatus().getConditions()
                        .stream().map(dc -> dc.getType()).collect(Collectors.joining("\n")));
                nextCondition.setReason("UNKNOWN_STATUS");

                var status = new ApicurioRegistry3Status();
                status.setConditions(List.of(nextCondition));

                return status;
            }

        } else {
            return errorStatus(new OperatorException("Expected deployment not found"));
        }
    }
}
