package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.AutoscalingSpec;
import io.apicurio.registry.operator.api.v1.spec.ComponentSpec;
import io.apicurio.registry.operator.api.v1.spec.IngressSpec;
import io.apicurio.registry.operator.api.v1.spec.NetworkPolicySpec;
import io.apicurio.registry.operator.api.v1.spec.PodDisruptionSpec;
import io.apicurio.registry.operator.api.v1.ConsolePlugin;
import io.apicurio.registry.operator.api.v1.spec.ConsolePluginSpec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
import io.apicurio.registry.operator.Configuration;
import io.apicurio.registry.operator.feat.GitOps;
import io.apicurio.registry.operator.feat.KubernetesOps;
import io.apicurio.registry.operator.resource.app.AppHorizontalPodAutoscalerResource;
import io.apicurio.registry.operator.resource.app.AppIngressResource;
import io.apicurio.registry.operator.resource.app.GitOpsSshServiceResource;
import io.apicurio.registry.operator.resource.app.AppNetworkPolicyResource;
import io.apicurio.registry.operator.resource.app.AppPodDisruptionBudgetResource;
import io.apicurio.registry.operator.resource.app.AppRoleBindingResource;
import io.apicurio.registry.operator.resource.app.AppRoleResource;
import io.apicurio.registry.operator.resource.app.AppServiceAccountResource;
import io.apicurio.registry.operator.resource.ui.UIDeploymentResource;
import io.apicurio.registry.operator.resource.ui.UIHorizontalPodAutoscalerResource;
import io.apicurio.registry.operator.resource.ui.UIIngressResource;
import io.apicurio.registry.operator.resource.ui.UINetworkPolicyResource;
import io.apicurio.registry.operator.resource.consoleplugin.ConsolePluginCRResource;
import io.apicurio.registry.operator.resource.consoleplugin.ConsolePluginDeploymentResource;
import io.apicurio.registry.operator.resource.consoleplugin.ConsolePluginServiceResource;
import io.apicurio.registry.operator.resource.ui.UIPodDisruptionBudgetResource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.CRDPresentActivationCondition;

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
                    .map(ComponentSpec::getIngress).map(IngressSpec::getEnabled).orElse(Boolean.TRUE);
            var hasIngressHost = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(AppSpec::getIngress).map(IngressSpec::getHost).map(host -> !isBlank(host))
                    .orElse(false);
            boolean isManaged = enabled && hasIngressHost;
            if (!isManaged) {
                ((AppIngressResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class AppPodDisruptionBudgetActivationCondition
            implements Condition<PodDisruptionBudget, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<PodDisruptionBudget, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isEnabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(ComponentSpec::getPodDisruptionBudget).map(PodDisruptionSpec::getEnabled)
                    .orElse(Boolean.TRUE);
            int numReplicas = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(ComponentSpec::getReplicas).orElse(1);
            boolean autoscalingCanScaleAboveOne = ofNullable(primary.getSpec())
                    .map(ApicurioRegistry3Spec::getApp).map(ComponentSpec::getAutoscaling)
                    .filter(a -> Boolean.TRUE.equals(a.getEnabled()))
                    .map(AutoscalingSpec::getMaxReplicas).map(max -> max > 1).orElse(false);

            boolean isManaged = isEnabled && (numReplicas > 1 || autoscalingCanScaleAboveOne);
            if (!isManaged) {
                ((AppPodDisruptionBudgetResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class AppNetworkPolicyActivationCondition
            implements Condition<NetworkPolicy, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<NetworkPolicy, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            Boolean isManaged = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(ComponentSpec::getNetworkPolicy).map(NetworkPolicySpec::getEnabled)
                    .orElse(Boolean.TRUE);
            if (!isManaged) {
                ((AppNetworkPolicyResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class AppHorizontalPodAutoscalerActivationCondition
            implements Condition<HorizontalPodAutoscaler, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<HorizontalPodAutoscaler, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isManaged = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(ComponentSpec::getAutoscaling).map(AutoscalingSpec::getEnabled)
                    .orElse(Boolean.FALSE);
            if (!isManaged) {
                ((AppHorizontalPodAutoscalerResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    // ===== KubernetesOps RBAC

    public static class KubernetesOpsServiceAccountActivationCondition
            implements Condition<ServiceAccount, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<ServiceAccount, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isManaged = KubernetesOps.isEnabled(primary);
            if (!isManaged) {
                ((AppServiceAccountResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class KubernetesOpsRoleActivationCondition
            implements Condition<Role, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<Role, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isManaged = KubernetesOps.isEnabled(primary);
            if (!isManaged) {
                ((AppRoleResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class KubernetesOpsRoleBindingActivationCondition
            implements Condition<RoleBinding, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<RoleBinding, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isManaged = KubernetesOps.isEnabled(primary);
            if (!isManaged) {
                ((AppRoleBindingResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    // ===== GitOps SSH Service

    public static class GitOpsSshServiceActivationCondition
            implements Condition<Service, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<Service, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isManaged = GitOps.isPushMode(primary);
            if (!isManaged) {
                ((GitOpsSshServiceResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    // ===== Registry UI

    public static class UIDeploymentActivationCondition implements Condition<Deployment, ApicurioRegistry3> {

        @Override
        public boolean isMet(DependentResource<Deployment, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

            var enabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(UiSpec::getEnabled).orElse(Boolean.TRUE);
            if (!enabled) {
                ((UIDeploymentResource) resource).delete(primary, context);
            }
            return enabled;
        }
    }

    public static class UIIngressActivationCondition implements Condition<Ingress, ApicurioRegistry3> {

        @Override
        public boolean isMet(DependentResource<Ingress, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

            var enabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(ComponentSpec::getIngress).map(IngressSpec::getEnabled).orElse(Boolean.TRUE);
            var hasIngressHost = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(UiSpec::getIngress).map(IngressSpec::getHost).map(host -> !isBlank(host))
                    .orElse(false);
            boolean isManaged = enabled && hasIngressHost;
            if (!isManaged) {
                ((UIIngressResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class UIPodDisruptionBudgetActivationCondition
            implements Condition<PodDisruptionBudget, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<PodDisruptionBudget, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isManaged = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(ComponentSpec::getPodDisruptionBudget).map(PodDisruptionSpec::getEnabled)
                    .orElse(Boolean.TRUE);
            if (!isManaged) {
                ((UIPodDisruptionBudgetResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class UINetworkPolicyActivationCondition
            implements Condition<NetworkPolicy, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<NetworkPolicy, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            Boolean isManaged = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(ComponentSpec::getNetworkPolicy).map(NetworkPolicySpec::getEnabled)
                    .orElse(Boolean.TRUE);
            if (!isManaged) {
                ((UINetworkPolicyResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class UIHorizontalPodAutoscalerActivationCondition
            implements Condition<HorizontalPodAutoscaler, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<HorizontalPodAutoscaler, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isManaged = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(ComponentSpec::getAutoscaling).map(AutoscalingSpec::getEnabled)
                    .orElse(Boolean.FALSE);
            if (!isManaged) {
                ((UIHorizontalPodAutoscalerResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    // ===== Console Plugin

    private static boolean isConsolePluginEnabled(ApicurioRegistry3 primary) {
        boolean specEnabled = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getConsolePlugin)
                .map(ConsolePluginSpec::getEnabled).orElse(Boolean.TRUE);
        boolean imageConfigured = Configuration.getConsolePluginImage().isPresent();
        return specEnabled && imageConfigured;
    }

    public static class ConsolePluginDeploymentActivationCondition
            implements Condition<Deployment, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<Deployment, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isManaged = isConsolePluginEnabled(primary);
            if (!isManaged) {
                ((ConsolePluginDeploymentResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class ConsolePluginServiceActivationCondition
            implements Condition<Service, ApicurioRegistry3> {
        @Override
        public boolean isMet(DependentResource<Service, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean isManaged = isConsolePluginEnabled(primary);
            if (!isManaged) {
                ((ConsolePluginServiceResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }

    public static class ConsolePluginCRActivationCondition
            implements Condition<ConsolePlugin, ApicurioRegistry3> {

        private final CRDPresentActivationCondition<ConsolePlugin, ApicurioRegistry3> crdCheck =
                new CRDPresentActivationCondition<>();

        @Override
        public boolean isMet(DependentResource<ConsolePlugin, ApicurioRegistry3> resource,
                             ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
            boolean crdPresent = crdCheck.isMet(resource, primary, context);
            if (!crdPresent) {
                return false;
            }
            boolean isManaged = isConsolePluginEnabled(primary);
            if (!isManaged) {
                ((ConsolePluginCRResource) resource).delete(primary, context);
            }
            return isManaged;
        }
    }
}
