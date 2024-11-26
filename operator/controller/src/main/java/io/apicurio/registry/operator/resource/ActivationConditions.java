package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.app.AppIngressResource;
import io.apicurio.registry.operator.resource.studioui.StudioUIDeploymentResource;
import io.apicurio.registry.operator.resource.studioui.StudioUIIngressResource;
import io.apicurio.registry.operator.resource.ui.UIIngressResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class ActivationConditions {

    private ActivationConditions() {
    }

    // ===== Registry App

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

    // ===== Registry UI

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

    // ===== Studio UI

    public static class StudioUIDeploymentActivationCondition
            implements Condition<Deployment, ApicurioRegistry3> {

        @Override
        public boolean isMet(DependentResource<Deployment, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

            var enabled = ofNullable(primary.getSpec().getStudioUi().getEnabled()).orElse(false);
            if (!enabled) {
                ((StudioUIDeploymentResource) resource).delete(primary, context);
            }
            return enabled;
        }
    }

    public static class StudioUIIngressActivationCondition implements Condition<Ingress, ApicurioRegistry3> {

        @Override
        public boolean isMet(DependentResource<Ingress, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

            var disabled = isBlank(primary.getSpec().getStudioUi().getHost());
            if (disabled) {
                ((StudioUIIngressResource) resource).delete(primary, context);
            }
            return !disabled;
        }
    }
}
