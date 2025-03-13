package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

import static io.apicurio.registry.operator.resource.ResourceFactory.*;

public class LabelDiscriminators {

    private LabelDiscriminators() {
    }

    // ===== Registry App

    public static class AppDeploymentDiscriminator extends ComponentLabelDiscriminator<Deployment> {

        public static final ResourceDiscriminator<Deployment, ApicurioRegistry3> INSTANCE = new AppDeploymentDiscriminator();

        public AppDeploymentDiscriminator() {
            super(COMPONENT_APP);
        }
    }

    public static class AppServiceDiscriminator extends ComponentLabelDiscriminator<Service> {

        public static final ResourceDiscriminator<Service, ApicurioRegistry3> INSTANCE = new AppServiceDiscriminator();

        public AppServiceDiscriminator() {
            super(COMPONENT_APP);
        }
    }

    public static class AppIngressDiscriminator extends ComponentLabelDiscriminator<Ingress> {

        public static final ResourceDiscriminator<Ingress, ApicurioRegistry3> INSTANCE = new AppIngressDiscriminator();

        public AppIngressDiscriminator() {
            super(COMPONENT_APP);
        }
    }

    public static class AppPodDisruptionBudgetDiscriminator extends ComponentLabelDiscriminator<PodDisruptionBudget> {

        public static final ResourceDiscriminator<PodDisruptionBudget, ApicurioRegistry3> INSTANCE = new AppPodDisruptionBudgetDiscriminator();

        public AppPodDisruptionBudgetDiscriminator() {
            super(COMPONENT_APP);
        }
    }

    public static class AppNetworkPolicyDiscriminator extends ComponentLabelDiscriminator<NetworkPolicy> {

        public static final ResourceDiscriminator<NetworkPolicy, ApicurioRegistry3> INSTANCE = new AppNetworkPolicyDiscriminator();

        public AppNetworkPolicyDiscriminator() {
            super(COMPONENT_APP);
        }
    }

    // ===== Registry UI

    public static class UIDeploymentDiscriminator extends ComponentLabelDiscriminator<Deployment> {

        public static final ResourceDiscriminator<Deployment, ApicurioRegistry3> INSTANCE = new UIDeploymentDiscriminator();

        public UIDeploymentDiscriminator() {
            super(COMPONENT_UI);
        }
    }

    public static class UIServiceDiscriminator extends ComponentLabelDiscriminator<Service> {

        public static ResourceDiscriminator<Service, ApicurioRegistry3> INSTANCE = new UIServiceDiscriminator();

        public UIServiceDiscriminator() {
            super(COMPONENT_UI);
        }
    }

    public static class UIIngressDiscriminator extends ComponentLabelDiscriminator<Ingress> {

        public static ResourceDiscriminator<Ingress, ApicurioRegistry3> INSTANCE = new UIIngressDiscriminator();

        public UIIngressDiscriminator() {
            super(COMPONENT_UI);
        }
    }

    public static class UiPodDisruptionBudgetDiscriminator extends ComponentLabelDiscriminator<PodDisruptionBudget> {

        public static final ResourceDiscriminator<PodDisruptionBudget, ApicurioRegistry3> INSTANCE = new AppPodDisruptionBudgetDiscriminator();

        public UiPodDisruptionBudgetDiscriminator() {
            super(COMPONENT_UI);
        }
    }

    public static class UINetworkPolicyDiscriminator extends ComponentLabelDiscriminator<NetworkPolicy> {

        public static final ResourceDiscriminator<NetworkPolicy, ApicurioRegistry3> INSTANCE = new AppNetworkPolicyDiscriminator();

        public UINetworkPolicyDiscriminator() {
            super(COMPONENT_UI);
        }
    }
}
