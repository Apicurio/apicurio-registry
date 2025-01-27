package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.ComponentSpec;
import io.apicurio.registry.operator.api.v1.spec.IngressSpec;
import io.apicurio.registry.operator.api.v1.spec.StudioUiSpec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
import io.apicurio.registry.operator.resource.app.AppIngressResource;
import io.apicurio.registry.operator.resource.app.AppPodDisruptionBudgetResource;
import io.apicurio.registry.operator.resource.studioui.StudioUIDeploymentResource;
import io.apicurio.registry.operator.resource.studioui.StudioUIIngressResource;
import io.apicurio.registry.operator.resource.studioui.StudioUIPodDisruptionBudgetResource;
import io.apicurio.registry.operator.resource.ui.UIIngressResource;
import io.apicurio.registry.operator.resource.ui.UIPodDisruptionBudgetResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
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
                    .map(AppSpec::getIngress).map(IngressSpec::getHost).map(host -> !isBlank(host))
                    .orElse(false);
            if (!enabled) {
                ((AppIngressResource) resource).delete(primary, context);
            }
            return enabled;
        }
    }

    public static class AppPodDisruptionBudgetActivationCondition
            implements Condition<PodDisruptionBudget, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<PodDisruptionBudget, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            Boolean isManaged = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(ComponentSpec::getManageDisruptionBudget).orElse(Boolean.TRUE);
            if (!isManaged) {
                ((AppPodDisruptionBudgetResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    // ===== Registry UI

    public static class UIIngressActivationCondition implements Condition<Ingress, ApicurioRegistry3> {

        @Override
        public boolean isMet(DependentResource<Ingress, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

            var enabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(UiSpec::getIngress).map(IngressSpec::getHost).map(host -> !isBlank(host))
                    .orElse(false);
            if (!enabled) {
                ((UIIngressResource) resource).delete(primary, context);
            }
            return enabled;
        }
    }

    public static class UIPodDisruptionBudgetActivationCondition
            implements Condition<PodDisruptionBudget, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<PodDisruptionBudget, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            Boolean isManaged = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(ComponentSpec::getManageDisruptionBudget).orElse(Boolean.TRUE);
            if (!isManaged) {
                ((UIPodDisruptionBudgetResource) resource).delete(primary, context);
            }
            return isManaged;
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
                    .map(StudioUiSpec::getIngress).map(IngressSpec::getHost).map(host -> !isBlank(host))
                    .orElse(false);
            if (!enabled) {
                ((StudioUIIngressResource) resource).delete(primary, context);
            }
            return enabled;
        }
    }

    public static class StudioUIPodDisruptionBudgetActivationCondition
            implements Condition<PodDisruptionBudget, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<PodDisruptionBudget, ApicurioRegistry3> resource,
                ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            Boolean isManaged = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getStudioUi)
                    .map(ComponentSpec::getManageDisruptionBudget).orElse(Boolean.TRUE);
            if (!isManaged) {
                ((StudioUIPodDisruptionBudgetResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

}
