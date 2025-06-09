package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.*;
import static io.apicurio.registry.operator.api.v1.status.ConditionStatus.FALSE;
import static io.apicurio.registry.operator.api.v1.status.ConditionStatus.TRUE;
import static io.apicurio.registry.operator.resource.Labels.getSelectorLabels;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.utils.Mapper.copy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class StatusConditionsTest extends ITBase {

    @Test
    void testStatusConditions() {
        var registry1 = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        registry1.getMetadata().setName("registry1");
        client.resource(registry1).create();

        var registry2 = copy(registry1);
        registry2.getMetadata().setName("registry2");
        // Dummy values to avoid
        // "admission webhook "validate.nginx.ingress.kubernetes.io" denied the request: host "simple-app.apps.cluster.example" and path "/" is already defined in ingress [...]"
        registry2.getSpec().getApp().getIngress().setHost("registry2-app.apps.cluster.example");
        registry2.getSpec().getUi().getIngress().setHost("registry2-ui.apps.cluster.example");
        client.resource(registry2).create();

        awaitConditionHasStatus(registry1, 1, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);

        // == Ready condition

        // Kill a pod
        // TODO: Retries?
        client.resource(client.pods().withLabels(getSelectorLabels(registry1, COMPONENT_APP)).list().getItems().get(0)).delete();
        await().ignoreExceptions().untilAsserted(() -> {
            var freshRegistry1 = client.resource(registry1).get();
            assertConditionHasStatus(freshRegistry1, 1, TYPE_READY, FALSE);
            var freshRegistry2 = client.resource(registry2).get();
            assertConditionHasStatus(freshRegistry2, 1, TYPE_READY, TRUE);
        });

        awaitConditionHasStatus(registry1, 1, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);

        // == ValidationError condition

        // @formatter:off
        registry1.getSpec().getApp().setPodTemplateSpec(new PodTemplateSpecBuilder()
                .withNewSpec()
                    .addNewContainer()
                        .withName(REGISTRY_APP_CONTAINER_NAME)
                        .addNewEnv()
                            .withName("foo")
                            .withValue("bar")
                        .endEnv()
                    .endContainer()
                .endSpec()
            .build());
        // @formatter:on
        client.resource(registry1).update();

        awaitConditionHasStatus(registry1, 2, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry1, 2, TYPE_VALIDATION_ERROR, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);

        // Check the transition and update times by causing another validation error
        // @formatter:off
        registry1.getSpec().getUi().setPodTemplateSpec(new PodTemplateSpecBuilder()
                .withNewSpec()
                    .addNewContainer()
                        .withName(REGISTRY_UI_CONTAINER_NAME)
                        .addNewEnv()
                            .withName("foo")
                            .withValue("bar")
                        .endEnv()
                    .endContainer()
                .endSpec()
            .build());
        // @formatter:on
        client.resource(registry1).update();

        await().ignoreExceptions().untilAsserted(() -> {
            var status = client.resource(registry1).get().getStatus();
            var lastTransitionTime = status.getConditions().stream()
                    .filter(c -> TYPE_VALIDATION_ERROR.equals(c.getType()))
                    .map(Condition::getLastTransitionTime)
                    .findFirst()
                    .get();
            var lastUpdateTime = status.getConditions().stream()
                    .filter(c -> TYPE_VALIDATION_ERROR.equals(c.getType()))
                    .map(Condition::getLastUpdateTime)
                    .findFirst()
                    .get();
            assertThat(lastUpdateTime).isAfter(lastTransitionTime);
        });

        registry1.getSpec().getApp().setPodTemplateSpec(new PodTemplateSpec());
        registry1.getSpec().getUi().setPodTemplateSpec(new PodTemplateSpec());
        client.resource(registry1).update();

        awaitConditionHasStatus(registry1, 1, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);

        // == OperatorError condition

        await().ignoreExceptions().untilAsserted(() -> {
            // TODO: This is not a great way of causing operator errors.
            // Moreover, we need to refresh the error otherwise the invalid value is removed from the CR.
            // Find something better that can be used in tests and is more reliable,
            // e.g. dynamic operator configuration from a ConfigMap to avoid using env. vars.
            // @formatter:off
            registry1.getSpec().getApp().setPodTemplateSpec(new PodTemplateSpecBuilder()
                    .withNewSpec()
                        .addNewContainer()
                            .withName(REGISTRY_APP_CONTAINER_NAME)
                            .withNewResources()
                                .addToRequests("intentionally-invalid", Quantity.parse("1"))
                            .endResources()
                        .endContainer()
                    .endSpec()
                    .build());
            // @formatter:on
            client.resource(registry1).update();
            var freshRegistry1 = client.resource(registry1).get();
            var freshRegistry2 = client.resource(registry2).get();
            assertConditionHasStatus(freshRegistry1, 2, TYPE_READY, TRUE);
            assertConditionHasStatus(freshRegistry1, 2, TYPE_OPERATOR_ERROR, TRUE);
            assertConditionHasStatus(freshRegistry2, 1, TYPE_READY, TRUE);
        });

        // Check the reason as well
        var status = client.resource(registry1).get().getStatus();
        assertThat(status.getConditions())
                .filteredOn(c -> TYPE_OPERATOR_ERROR.equals(c.getType()))
                .map(Condition::getReason)
                .containsExactly("KubernetesClientException");

        registry1.getSpec().getApp().setPodTemplateSpec(new PodTemplateSpec());
        client.resource(registry1).update();

        awaitConditionHasStatus(registry1, 1, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);
    }

    private static void awaitConditionHasStatus(ApicurioRegistry3 registry, int total, String conditionType, ConditionStatus conditionStatus) {
        await().ignoreExceptions().untilAsserted(() -> {
            var freshRegistry = client.resource(registry).get();
            assertConditionHasStatus(freshRegistry, total, conditionType, conditionStatus);
        });
    }

    private static void assertConditionHasStatus(ApicurioRegistry3 registry, int total, String conditionType, ConditionStatus conditionStatus) {
        assertThat(registry.getStatus().getConditions())
                .hasSize(total)
                .filteredOn(c -> conditionType.equals(c.getType()))
                .hasSize(1)
                .map(Condition::getStatus)
                .containsExactly(conditionStatus);
    }
}
