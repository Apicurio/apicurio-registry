package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.api.v1.spec.AutoscalingSpec;
import io.apicurio.registry.operator.api.v1.spec.ComponentSpec;
import io.apicurio.registry.operator.api.v1.spec.PodDisruptionSpec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.resource.ActivationConditions.isPodDisruptionBudgetManaged;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PodDisruptionBudgetConditionTest {

    @Test
    void noBudgetForASinglePod() {
        assertFalse(isPodDisruptionBudgetManaged(null));
        assertFalse(isPodDisruptionBudgetManaged(new UiSpec()));
        assertFalse(isPodDisruptionBudgetManaged(component(1, null, null)));
    }

    @Test
    void budgetWithMoreThanOnePod() {
        assertTrue(isPodDisruptionBudgetManaged(component(2, null, null)));
    }

    @Test
    void budgetWhenAutoscalingCanGoAboveOne() {
        assertTrue(isPodDisruptionBudgetManaged(component(1, null, autoscaling(true, 3))));
        assertFalse(isPodDisruptionBudgetManaged(component(1, null, autoscaling(true, 1))));
        assertFalse(isPodDisruptionBudgetManaged(component(1, null, autoscaling(false, 3))));
    }

    @Test
    void noBudgetWhenDisabled() {
        assertFalse(isPodDisruptionBudgetManaged(component(3, false, autoscaling(true, 5))));
    }

    private static ComponentSpec component(Integer replicas, Boolean pdbEnabled, AutoscalingSpec autoscaling) {
        UiSpec spec = new UiSpec();
        spec.setReplicas(replicas);
        spec.setAutoscaling(autoscaling);
        if (pdbEnabled != null) {
            PodDisruptionSpec pdb = new PodDisruptionSpec();
            pdb.setEnabled(pdbEnabled);
            spec.setPodDisruptionBudget(pdb);
        }
        return spec;
    }

    private static AutoscalingSpec autoscaling(boolean enabled, int maxReplicas) {
        AutoscalingSpec autoscaling = new AutoscalingSpec();
        autoscaling.setEnabled(enabled);
        autoscaling.setMaxReplicas(maxReplicas);
        return autoscaling;
    }
}
