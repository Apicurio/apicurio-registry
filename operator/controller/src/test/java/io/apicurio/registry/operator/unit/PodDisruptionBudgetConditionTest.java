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
        assertTrue(isPodDisruptionBudgetManaged(component(1, null, autoscaling(true, null, 3))));
        assertFalse(isPodDisruptionBudgetManaged(component(1, null, autoscaling(true, null, 1))));
        assertFalse(isPodDisruptionBudgetManaged(component(1, null, autoscaling(false, null, 3))));
    }

    @Test
    void autoscalingMaximumDefaultsToTheMinimum() {
        // The HorizontalPodAutoscaler's maxReplicas defaults to minReplicas, which defaults to 1
        assertTrue(isPodDisruptionBudgetManaged(component(null, null, autoscaling(true, 2, null))));
        assertFalse(isPodDisruptionBudgetManaged(component(null, null, autoscaling(true, null, null))));
    }

    @Test
    void staticReplicasAreIgnoredWithAutoscaling() {
        // The autoscaler, not replicas, decides how many pods run
        assertFalse(isPodDisruptionBudgetManaged(component(2, null, autoscaling(true, null, 1))));
        assertTrue(isPodDisruptionBudgetManaged(component(2, null, autoscaling(false, null, 1))));
    }

    @Test
    void noBudgetWhenDisabled() {
        assertFalse(isPodDisruptionBudgetManaged(component(3, false, autoscaling(true, null, 5))));
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

    private static AutoscalingSpec autoscaling(boolean enabled, Integer minReplicas, Integer maxReplicas) {
        AutoscalingSpec autoscaling = new AutoscalingSpec();
        autoscaling.setEnabled(enabled);
        autoscaling.setMinReplicas(minReplicas);
        autoscaling.setMaxReplicas(maxReplicas);
        return autoscaling;
    }
}
