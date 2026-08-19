package io.apicurio.registry.operator.mock;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.AutoscalingSpec;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock-server equivalents of {@code HorizontalPodAutoscalerITTest}.
 */
@QuarkusTest
public class HorizontalPodAutoscalerReconcileTest extends MockServerTestBase {

    @Test
    void hpaCreated() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        var autoscaling = new AutoscalingSpec();
        autoscaling.setEnabled(true);
        autoscaling.setMinReplicas(1);
        autoscaling.setMaxReplicas(3);
        autoscaling.setTargetCPUUtilizationPercentage(70);
        registry.getSpec().getApp().setAutoscaling(autoscaling);
        createRegistry(registry);

        String hpaName = registry.getMetadata().getName() + "-" + COMPONENT_APP + "-horizontalpodautoscaler";
        HorizontalPodAutoscaler hpa = awaitResourceExists(hpaName,
                () -> client.autoscaling().v2().horizontalPodAutoscalers()
                        .inNamespace(namespace).withName(hpaName).get());

        assertThat(hpa.getSpec().getScaleTargetRef().getName())
                .isEqualTo(deploymentName(registry, COMPONENT_APP));
        assertThat(hpa.getSpec().getMinReplicas()).isEqualTo(1);
        assertThat(hpa.getSpec().getMaxReplicas()).isEqualTo(3);
        assertThat(hpa.getSpec().getMetrics()).hasSize(1);
        assertThat(hpa.getSpec().getMetrics().get(0).getResource().getName()).isEqualTo("cpu");
        assertThat(hpa.getSpec().getMetrics().get(0).getResource().getTarget()
                .getAverageUtilization()).isEqualTo(70);
    }

    @Test
    void hpaCreatedWithMemory() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        var autoscaling = new AutoscalingSpec();
        autoscaling.setEnabled(true);
        autoscaling.setMinReplicas(2);
        autoscaling.setMaxReplicas(5);
        autoscaling.setTargetCPUUtilizationPercentage(60);
        autoscaling.setTargetMemoryUtilizationPercentage(75);
        registry.getSpec().getApp().setAutoscaling(autoscaling);
        createRegistry(registry);

        String hpaName = registry.getMetadata().getName() + "-" + COMPONENT_APP + "-horizontalpodautoscaler";
        HorizontalPodAutoscaler hpa = awaitResourceExists(hpaName,
                () -> client.autoscaling().v2().horizontalPodAutoscalers()
                        .inNamespace(namespace).withName(hpaName).get());

        assertThat(hpa.getSpec().getMinReplicas()).isEqualTo(2);
        assertThat(hpa.getSpec().getMaxReplicas()).isEqualTo(5);
        assertThat(hpa.getSpec().getMetrics()).hasSize(2);
    }

    @Test
    void hpaNotCreatedWhenDisabled() {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        createRegistry(registry);

        awaitDeploymentExists(deploymentName(registry, COMPONENT_APP));

        String hpaName = registry.getMetadata().getName() + "-" + COMPONENT_APP + "-horizontalpodautoscaler";
        awaitResourceAbsent(() -> client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(namespace).withName(hpaName).get());
    }
}
