package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.app.AppIngressResource;
import io.apicurio.registry.operator.resource.ui.UIIngressResource;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static io.apicurio.registry.operator.utils.Utils.isBlank;

public class ActivationConditions {

    private ActivationConditions() {
    }

    public static class AppIngressActivationCondition implements Condition<Ingress, ApicurioRegistry3> {

        @Override
        public boolean isMet(DependentResource<Ingress, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

            var disabled = isBlank(primary.getSpec().getApp().getHost());
            if (disabled) {
                ((AppIngressResource) resource).delete(primary, context);
            }
            return !disabled;
        }
    }

    public static class UIIngressActivationCondition implements Condition<Ingress, ApicurioRegistry3> {

        @Override
        public boolean isMet(DependentResource<Ingress, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

            var disabled = isBlank(primary.getSpec().getUi().getHost());
            if (disabled) {
                ((UIIngressResource) resource).delete(primary, context);
            }
            return !disabled;
        }
    }
}
