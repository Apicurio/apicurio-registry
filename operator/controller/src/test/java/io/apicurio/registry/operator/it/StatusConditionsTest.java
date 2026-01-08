package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.status.Condition;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.apicurio.registry.operator.utils.K8sCell;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_OPERATOR_ERROR;
import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_READY;
import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_VALIDATION_ERROR;
import static io.apicurio.registry.operator.api.v1.status.ConditionStatus.FALSE;
import static io.apicurio.registry.operator.api.v1.status.ConditionStatus.TRUE;
import static io.apicurio.registry.operator.resource.Labels.getSelectorLabels;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.utils.K8sCell.k8sCellCreate;
import static io.apicurio.registry.operator.utils.Mapper.copyAsNew;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(FEATURE)
public class StatusConditionsTest extends ITBase {

    @Test
    void testStatusConditions() {
        final var registry1 = k8sCellCreate(client, () -> {
            var r = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
            r.getMetadata().setName("registry1");
            return r;
        });

        final var registry2 = k8sCellCreate(client, () -> {
            // Must use copyAsNew instead of copy.
            // io.fabric8.kubernetes.client.KubernetesClientException: Failure executing: POST at: [...]/apicurioregistries3.
            // Message: resourceVersion should not be set on objects to be created. Received status:
            // Status(apiVersion=v1, code=500, details=null, kind=Status, message=resourceVersion should not be set on objects to be created, metadata=ListMeta(_continue=null, remainingItemCount=null, resourceVersion=null, selfLink=null, additionalProperties={}), reason=null, status=Failure, additionalProperties={}).
            var r = copyAsNew(registry1.getCached());
            r.getMetadata().setName("registry2");
            // Dummy values to avoid: admission webhook "validate.nginx.ingress.kubernetes.io" denied the request: host "simple-app.apps.cluster.example" and path "/" is already defined in ingress [...]
            r.getSpec().getApp().getIngress().setHost("registry2-app.apps.cluster.example");
            r.getSpec().getUi().getIngress().setHost("registry2-ui.apps.cluster.example");
            return r;
        });

        awaitConditionHasStatus(registry1, 1, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);

        // == Ready condition

        // Kill a pod
        // TODO: Retries?
        client.resource(client.pods().withLabels(getSelectorLabels(registry1.getCached(), COMPONENT_APP)).list().getItems().get(0)).delete();
        await().ignoreExceptions().untilAsserted(() -> {
            assertConditionHasStatus(registry1.get(), 1, TYPE_READY, FALSE);
            assertConditionHasStatus(registry2.get(), 1, TYPE_READY, TRUE);
        });

        awaitConditionHasStatus(registry1, 1, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);

        // == ValidationError condition
        registry1.update(r -> {
            // @formatter:off
            r.getSpec().getApp().setPodTemplateSpec(
                new PodTemplateSpecBuilder()
                    .withNewSpec()
                        .addNewContainer()
                            .withName(REGISTRY_APP_CONTAINER_NAME)
                            .addNewEnv()
                                .withName("foo")
                                .withValue("bar")
                            .endEnv()
                        .endContainer()
                    .endSpec()
                .build()
            );
            // @formatter:on
        });

        awaitConditionHasStatus(registry1, 2, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry1, 2, TYPE_VALIDATION_ERROR, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);

        // Check the transition and update times by causing another validation error
        registry1.update(r -> {
            // @formatter:off
            r.getSpec().getUi().setPodTemplateSpec(
                new PodTemplateSpecBuilder()
                    .withNewSpec()
                        .addNewContainer()
                            .withName(REGISTRY_UI_CONTAINER_NAME)
                            .addNewEnv()
                                .withName("foo")
                                .withValue("bar")
                            .endEnv()
                        .endContainer()
                    .endSpec()
                .build()
            );
            // @formatter:on
        });

        await().ignoreExceptions().untilAsserted(() -> {
            var status = registry1.get().getStatus();
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

        registry1.update(r -> {
            r.getSpec().getApp().setPodTemplateSpec(new PodTemplateSpec());
            r.getSpec().getUi().setPodTemplateSpec(new PodTemplateSpec());
        });

        awaitConditionHasStatus(registry1, 1, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);

        // == OperatorError condition

        await().ignoreExceptions().untilAsserted(() -> {
            // TODO: This is not a great way of causing operator errors.
            // Moreover, we need to refresh the error otherwise the invalid value is removed from the CR.
            // Find something better that can be used in tests and is more reliable,
            // e.g. dynamic operator configuration from a ConfigMap to avoid using env. vars.
            registry1.update(r -> {
                // @formatter:off
                r.getSpec().getApp().setPodTemplateSpec(new PodTemplateSpecBuilder()
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
            });
            assertConditionHasStatus(registry1.get(), 2, TYPE_READY, TRUE);
            assertConditionHasStatus(registry1.getCached(), 2, TYPE_OPERATOR_ERROR, TRUE);
            assertConditionHasStatus(registry2.get(), 1, TYPE_READY, TRUE);
        });

        // Check the reason as well
        var status = registry1.get().getStatus();
        assertThat(status.getConditions())
                .filteredOn(c -> TYPE_OPERATOR_ERROR.equals(c.getType()))
                .map(Condition::getReason)
                .containsExactly("KubernetesClientException");

        registry1.update(r -> {
            r.getSpec().getApp().setPodTemplateSpec(new PodTemplateSpec());
        });

        awaitConditionHasStatus(registry1, 1, TYPE_READY, TRUE);
        awaitConditionHasStatus(registry2, 1, TYPE_READY, TRUE);
    }

    private static void awaitConditionHasStatus(K8sCell<ApicurioRegistry3> registry, int total, String conditionType, ConditionStatus conditionStatus) {
        await().ignoreExceptions().untilAsserted(() -> {
            assertConditionHasStatus(registry.get(), total, conditionType, conditionStatus);
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
