package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminators;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_POD_DISRUPTION_BUDGET_KEY;

// spotless:off
@KubernetesDependent(
        labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component=" + COMPONENT_APP,
        resourceDiscriminator = LabelDiscriminators.AppPodDisruptionBudgetDiscriminator.class
)
// spotless:on
public class AppPodDisruptionBudgetResource
        extends CRUDKubernetesDependentResource<PodDisruptionBudget, ApicurioRegistry3> {

    public AppPodDisruptionBudgetResource() {
        super(PodDisruptionBudget.class);
    }

    @Override
    protected PodDisruptionBudget desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        PodDisruptionBudget pdb = APP_POD_DISRUPTION_BUDGET_KEY.getFactory().apply(primary);
        return pdb;
    }
}
