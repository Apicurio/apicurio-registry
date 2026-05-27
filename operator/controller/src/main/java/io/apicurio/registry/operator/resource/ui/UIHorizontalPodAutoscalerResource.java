package io.apicurio.registry.operator.resource.ui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.apicurio.registry.operator.resource.ResourceKey.UI_HORIZONTAL_POD_AUTOSCALER_KEY;

@KubernetesDependent
public class UIHorizontalPodAutoscalerResource
        extends CRUDKubernetesDependentResource<HorizontalPodAutoscaler, ApicurioRegistry3> {

    public UIHorizontalPodAutoscalerResource() {
        super(HorizontalPodAutoscaler.class);
    }

    @Override
    protected HorizontalPodAutoscaler desired(ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {
        return UI_HORIZONTAL_POD_AUTOSCALER_KEY.getFactory().apply(primary);
    }
}
