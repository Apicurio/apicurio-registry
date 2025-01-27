package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.ComponentSpec;
import io.apicurio.registry.operator.api.v1.spec.IngressSpec;
import io.apicurio.registry.operator.api.v1.spec.StudioUiSpec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
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

            var enabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(ComponentSpec::getManageIngress).orElse(Boolean.TRUE);
            var hasIngressHost = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(AppSpec::getIngress).map(IngressSpec::getHost).map(host -> !isBlank(host))
                    .orElse(false);
            if (!enabled || !hasIngressHost) {
                ((AppIngressResource) resource).delete(primary, context);
            }
            return enabled && hasIngressHost;
        }
    }

    // ===== Registry UI

    public static class UIIngressActivationCondition implements Condition<Ingress, ApicurioRegistry3> {

        @Override
        public boolean isMet(DependentResource<Ingress, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

            var enabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(ComponentSpec::getManageIngress).orElse(Boolean.TRUE);
            var hasIngressHost = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(UiSpec::getIngress).map(IngressSpec::getHost).map(host -> !isBlank(host))
                    .orElse(false);
            if (!enabled || !hasIngressHost) {
                ((UIIngressResource) resource).delete(primary, context);
            }
            return enabled && hasIngressHost;
        }
    }

    // ===== Studio UI

    public static class StudioUIDeploymentActivationCondition
            implements Condition<Deployment, ApicurioRegistry3> {

        @Override
        public boolean isMet(DependentResource<Deployment, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

            var enabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getStudioUi)
                    .map(StudioUiSpec::getEnabled).orElse(false);
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

            var enabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getStudioUi)
                    .map(ComponentSpec::getManageIngress).orElse(Boolean.TRUE);
            var hasIngressHost = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getStudioUi)
                    .map(StudioUiSpec::getIngress).map(IngressSpec::getHost).map(host -> !isBlank(host))
                    .orElse(false);
            if (!enabled || !hasIngressHost) {
                ((StudioUIIngressResource) resource).delete(primary, context);
            }
            return enabled && hasIngressHost;
        }
    }
}
