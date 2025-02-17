package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Status;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;

public class StatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(StatusUpdater.class);

    public static final String ERROR_TYPE = "ERROR";
    public static final String READY_TYPE = "READY";
    public static final String STARTED_TYPE = "STARTED";
    public static final String UNKNOWN_TYPE = "UNKNOWN";

    private final ApicurioRegistry3 registry;

    public StatusUpdater(ApicurioRegistry3 registry) {
        this.registry = registry;
    }

    private Condition defaultCondition() {
        var condition = new Condition();
        condition.setObservedGeneration(
                registry.getMetadata() == null ? null : registry.getMetadata().getGeneration());
        condition.setLastTransitionTime(now());
        return condition;
    }

    public void updateWithException(Exception e) {
        // TODO: Ignore some KubernetesClientException-s that are caused by update conflicts.
        var errorCondition = defaultCondition();
        errorCondition.setType(ERROR_TYPE);
        errorCondition.setStatus(ConditionStatus.TRUE);
        errorCondition.setReason("ERROR_STATUS");
        errorCondition.setMessage(Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString)
                .collect(Collectors.joining("\n"))); // TODO: Make the message more useful.

        registry.withStatus().setConditions(List.of(errorCondition));
    }

    private Condition getOrCreateCondition(ApicurioRegistry3Status status, String type) {
        var conditions = status.getConditions().stream().filter(c -> type.equals(c.getType())).toList();
        if (conditions.size() > 1) {
            throw new OperatorException("Duplicate conditions: " + conditions);
        } else if (conditions.size() == 1) {
            return conditions.get(0);
        } else {
            var newCondition = defaultCondition();
            newCondition.setType(type);
            status.getConditions().add(newCondition);
            return newCondition;
        }
    }

    public void update(Deployment deployment) {
        requireNonNull(deployment);
        log.trace("Setting status based on Deployment:\n{}", toYAML(deployment.getStatus()));

        // Remove error condition if present
        registry.withStatus().getConditions().removeIf(c -> ERROR_TYPE.equals(c.getType()));

        // Ready
        var readyCondition = getOrCreateCondition(registry.getStatus(), READY_TYPE);
        if (deployment.getStatus() != null && deployment.getStatus().getConditions().stream()
                .anyMatch(condition -> condition.getType().equals("Available")
                        && condition.getStatus().equalsIgnoreCase(ConditionStatus.TRUE.getValue()))) {
            readyCondition.setStatus(ConditionStatus.TRUE);
            readyCondition.setReason("DEPLOYMENT_AVAILABLE"); // TODO: Constants
            readyCondition.setMessage("App Deployment is available."); // TODO: What about other components?
        } else {
            readyCondition.setStatus(ConditionStatus.FALSE);
            readyCondition.setReason("DEPLOYMENT_NOT_AVAILABLE");
            readyCondition.setMessage("App Deployment is not available.");
        }

        // Started
        var startedCondition = getOrCreateCondition(registry.getStatus(), STARTED_TYPE); // TODO: Do we
                                                                                         // actually need
                                                                                         // this?
        if (deployment.getStatus() != null && deployment.getStatus().getConditions().size() > 0) {
            startedCondition.setStatus(ConditionStatus.TRUE);
            startedCondition.setReason("DEPLOYMENT_STARTED");
            startedCondition.setMessage("App Deployment conditions: " + deployment.getStatus().getConditions()
                    .stream().map(dc -> dc.getType() + " = " + dc.getStatus())
                    .collect(Collectors.joining(", ")) + ".");
        } else {
            startedCondition.setStatus(ConditionStatus.UNKNOWN);
            startedCondition.setReason("DEPLOYMENT_STATUS_UNKNOWN");
            startedCondition.setMessage("No App Deployment conditions available."); // TODO: This does not
                                                                                    // make much sense.
        }
    }
}
