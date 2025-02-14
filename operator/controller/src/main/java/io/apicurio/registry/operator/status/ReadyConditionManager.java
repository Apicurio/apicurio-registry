package io.apicurio.registry.operator.status;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_READY;

/**
 * Manages the condition that reports operand readiness.
 * <p>
 * In order to determine which components are active while avoiding complex/duplicated logic,
 * {@link io.apicurio.registry.operator.status.ReadyConditionManager#recordIsActive(io.apicurio.registry.operator.resource.ResourceKey)}
 * is called when a dependent resource is processed.
 */
public class ReadyConditionManager extends AbstractConditionManager {

    private static final String REASON_DEPLOYMENT_AVAILABLE = "ActiveDeploymentsAvailable";
    private static final String REASON_DEPLOYMENT_UNAVAILABLE = "ActiveDeploymentUnavailable";

    private final Set<ResourceKey<Deployment>> isActive = new HashSet<>();

    ReadyConditionManager() {
        resetCondition();
    }

    @Override
    void resetCondition() {
        current = new Condition();
        current.setType(TYPE_READY);
        isActive.clear();
    }

    public synchronized void recordIsActive(ResourceKey<Deployment> deploymentResourceKey) {
        isActive.add(deploymentResourceKey);
    }

    @Override
    void updateCondition(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        isActive.add(ResourceKey.APP_DEPLOYMENT_KEY); // Always active
        var ready = true;
        List<String> unavailable = new ArrayList<>();
        for (ResourceKey<Deployment> deploymentResourceKey : isActive) {
            var r = context.getSecondaryResource(deploymentResourceKey.getKlass(), deploymentResourceKey.getDiscriminator())
                    .map(d -> {
                        return d.getStatus() != null && d.getStatus().getConditions().stream()
                                .anyMatch(condition -> "Available".equals(condition.getType())
                                                       && condition.getStatus().equalsIgnoreCase(ConditionStatus.TRUE.getValue()));
                    }).orElse(false);
            if (!r) {
                unavailable.add(deploymentResourceKey.getId());
            }
            ready = ready && r;
        }
        if (ready) {
            current.setStatus(ConditionStatus.TRUE);
            current.setReason(REASON_DEPLOYMENT_AVAILABLE);
            current.setMessage("All active Deployments are available.");
        } else {
            current.setStatus(ConditionStatus.FALSE);
            current.setReason(REASON_DEPLOYMENT_UNAVAILABLE);
            current.setMessage("Some active Deployments are not available: " + String.join(", ", unavailable) + ".");
        }
    }

    @Override
    boolean show() {
        return true; // Always show
    }
}
