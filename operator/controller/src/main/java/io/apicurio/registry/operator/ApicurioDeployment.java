package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.apicurio.registry.operator.Constants.*;

@KubernetesDependent(labelSelector = LABEL_SELECTOR_KEY)
public class ApicurioDeployment extends CRUDKubernetesDependentResource<Deployment, ApicurioRegistry3> {

    public ApicurioDeployment() {
        super(Deployment.class);
    }

    public static String name(ApicurioRegistry3 apicurioRegistry) {
        return apicurioRegistry.getMetadata().getName();
    }

    @Override
    protected Deployment desired(ApicurioRegistry3 apicurioRegistry, Context<ApicurioRegistry3> context) {
        var labels = apicurioRegistry.getMetadata().getLabels();
        labels.putAll(Constants.defaultLabels(apicurioRegistry));
        return new DeploymentBuilder().withNewMetadata().withName(ApicurioDeployment.name(apicurioRegistry))
                .withNamespace(apicurioRegistry.getMetadata().getNamespace())
                .withOwnerReferences(new OwnerReferenceBuilder().withController(true)
                        .withBlockOwnerDeletion(true).withApiVersion(apicurioRegistry.getApiVersion())
                        .withKind(apicurioRegistry.getKind())
                        .withName(apicurioRegistry.getMetadata().getName())
                        .withUid(apicurioRegistry.getMetadata().getUid()).build())
                .withLabels(labels).endMetadata().withNewSpec().withNewSelector()
                .addToMatchLabels(LABEL_SELECTOR_KEY, LABEL_SELECTOR_VALUE).endSelector()
                .withReplicas(DEFAULT_REPLICAS).withNewTemplate().withNewMetadata().withLabels(labels)
                .endMetadata().withNewSpec().addNewContainer().withName(CONTAINER_NAME)
                .withImage(DEFAULT_CONTAINER_IMAGE).withImagePullPolicy("Always").withNewResources()
                .withRequests(DEFAULT_REQUESTS).withLimits(DEFAULT_LIMITS).endResources()
                .withReadinessProbe(DEFAULT_READINESS_PROBE).withLivenessProbe(DEFAULT_LIVENESS_PROBE)
                .endContainer().endSpec().endTemplate().withNewStrategy().withNewRollingUpdate()
                .withNewMaxUnavailable().withValue(1).endMaxUnavailable().withNewMaxSurge().withValue(1)
                .endMaxSurge().endRollingUpdate().endStrategy().endSpec().build();
    }
}
