package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.AutoscalingSpec;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(FEATURE)
public class HorizontalPodAutoscalerITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(HorizontalPodAutoscalerITTest.class);

    @Test
    void testHorizontalPodAutoscaler() {
        ApicurioRegistry3 registry = ResourceFactory.deserialize(
                "/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);

        var autoscaling = new AutoscalingSpec();
        autoscaling.setEnabled(true);
        autoscaling.setMinReplicas(1);
        autoscaling.setMaxReplicas(3);
        autoscaling.setTargetCPUUtilizationPercentage(70);
        registry.getSpec().getApp().setAutoscaling(autoscaling);

        client.resource(registry).create();

        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        String hpaName = registry.getMetadata().getName() + "-" + ResourceFactory.COMPONENT_APP
                + "-horizontalpodautoscaler";
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            HorizontalPodAutoscaler hpa = client.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(namespace).withName(hpaName).get();
            assertThat(hpa).isNotNull();
            assertThat(hpa.getSpec().getScaleTargetRef().getName())
                    .isEqualTo(registry.getMetadata().getName() + "-app-deployment");
            assertThat(hpa.getSpec().getMinReplicas()).isEqualTo(1);
            assertThat(hpa.getSpec().getMaxReplicas()).isEqualTo(3);
            assertThat(hpa.getSpec().getMetrics()).hasSize(1);
            assertThat(hpa.getSpec().getMetrics().get(0).getResource().getName()).isEqualTo("cpu");
            assertThat(hpa.getSpec().getMetrics().get(0).getResource().getTarget()
                    .getAverageUtilization()).isEqualTo(70);
        });
    }

    @Test
    void testHorizontalPodAutoscalerWithMemory() {
        ApicurioRegistry3 registry = ResourceFactory.deserialize(
                "/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);

        var autoscaling = new AutoscalingSpec();
        autoscaling.setEnabled(true);
        autoscaling.setMinReplicas(2);
        autoscaling.setMaxReplicas(5);
        autoscaling.setTargetCPUUtilizationPercentage(60);
        autoscaling.setTargetMemoryUtilizationPercentage(75);
        registry.getSpec().getApp().setAutoscaling(autoscaling);

        client.resource(registry).create();

        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 2);

        String hpaName = registry.getMetadata().getName() + "-" + ResourceFactory.COMPONENT_APP
                + "-horizontalpodautoscaler";
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            HorizontalPodAutoscaler hpa = client.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(namespace).withName(hpaName).get();
            assertThat(hpa).isNotNull();
            assertThat(hpa.getSpec().getMinReplicas()).isEqualTo(2);
            assertThat(hpa.getSpec().getMaxReplicas()).isEqualTo(5);
            assertThat(hpa.getSpec().getMetrics()).hasSize(2);
        });
    }

    @Test
    void testHorizontalPodAutoscalerNotCreatedWhenDisabled() {
        ApicurioRegistry3 registry = ResourceFactory.deserialize(
                "/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        client.resource(registry).create();

        checkDeploymentExists(registry, ResourceFactory.COMPONENT_APP, 1);

        String hpaName = registry.getMetadata().getName() + "-" + ResourceFactory.COMPONENT_APP
                + "-horizontalpodautoscaler";
        await().during(SHORT_DURATION).atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
            HorizontalPodAutoscaler hpa = client.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(namespace).withName(hpaName).get();
            assertThat(hpa).isNull();
        });
    }
}
