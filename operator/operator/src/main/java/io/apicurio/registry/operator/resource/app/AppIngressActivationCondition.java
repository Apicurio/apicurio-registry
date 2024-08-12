package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static java.lang.Boolean.TRUE;

public class AppIngressActivationCondition implements Condition<Ingress, ApicurioRegistry3> {

    @Override
    public boolean isMet(DependentResource<Ingress, ApicurioRegistry3> resource, ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {

        var disabled = primary.getSpec().getApp() != null && primary.getSpec().getApp().getFeatures() != null
                && primary.getSpec().getApp().getFeatures().getIngress() != null
                && TRUE.equals(primary.getSpec().getApp().getFeatures().getIngress().getDisabled());

        if (disabled) {
            ((AppIngressResource) resource).delete(primary, context);
        }

        return !disabled;
    }
}
