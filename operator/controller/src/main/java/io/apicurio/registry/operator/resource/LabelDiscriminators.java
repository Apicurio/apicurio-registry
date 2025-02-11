package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.*;

public class LabelDiscriminators {

    private LabelDiscriminators() {
    }

    // ===== Registry App

    public static class AppDeploymentDiscriminator extends LabelDiscriminator<Deployment> {

        public static final ResourceDiscriminator<Deployment, ApicurioRegistry3> INSTANCE = new AppDeploymentDiscriminator();

        public AppDeploymentDiscriminator() {
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_APP
            ));
        }
    }

    public static class AppServiceDiscriminator extends LabelDiscriminator<Service> {

        public static final ResourceDiscriminator<Service, ApicurioRegistry3> INSTANCE = new AppServiceDiscriminator();

        public AppServiceDiscriminator() {
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_APP
            ));
        }
    }

    public static class AppIngressDiscriminator extends LabelDiscriminator<Ingress> {

        public static final ResourceDiscriminator<Ingress, ApicurioRegistry3> INSTANCE = new AppIngressDiscriminator();

        public AppIngressDiscriminator() {
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_APP
            ));
        }
    }

    public static class AppPodDisruptionBudgetDiscriminator extends LabelDiscriminator<PodDisruptionBudget> {

        public static final ResourceDiscriminator<PodDisruptionBudget, ApicurioRegistry3> INSTANCE = new AppPodDisruptionBudgetDiscriminator();

        public AppPodDisruptionBudgetDiscriminator() {
            // spotless:off
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_APP
            ));
            // spotless:on
        }
    }

    // ===== Registry UI

    public static class UIDeploymentDiscriminator extends LabelDiscriminator<Deployment> {

        public static final ResourceDiscriminator<Deployment, ApicurioRegistry3> INSTANCE = new UIDeploymentDiscriminator();

        public UIDeploymentDiscriminator() {
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_UI
            ));
        }
    }

    public static class UIServiceDiscriminator extends LabelDiscriminator<Service> {

        public static ResourceDiscriminator<Service, ApicurioRegistry3> INSTANCE = new UIServiceDiscriminator();

        public UIServiceDiscriminator() {
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_UI
            ));
        }
    }

    public static class UIIngressDiscriminator extends LabelDiscriminator<Ingress> {

        public static ResourceDiscriminator<Ingress, ApicurioRegistry3> INSTANCE = new UIIngressDiscriminator();

        public UIIngressDiscriminator() {
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_UI
            ));
        }
    }

    public static class UiPodDisruptionBudgetDiscriminator extends LabelDiscriminator<PodDisruptionBudget> {

        public static final ResourceDiscriminator<PodDisruptionBudget, ApicurioRegistry3> INSTANCE = new AppPodDisruptionBudgetDiscriminator();

        public UiPodDisruptionBudgetDiscriminator() {
            // spotless:off
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_UI
            ));
            // spotless:on
        }
    }

    // ===== Studio UI

    public static class StudioUIDeploymentDiscriminator extends LabelDiscriminator<Deployment> {

        public static final ResourceDiscriminator<Deployment, ApicurioRegistry3> INSTANCE = new StudioUIDeploymentDiscriminator();

        public StudioUIDeploymentDiscriminator() {
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_STUDIO_UI
            ));
        }
    }

    public static class StudioUIServiceDiscriminator extends LabelDiscriminator<Service> {

        public static ResourceDiscriminator<Service, ApicurioRegistry3> INSTANCE = new StudioUIServiceDiscriminator();

        public StudioUIServiceDiscriminator() {
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_STUDIO_UI
            ));
        }
    }

    public static class StudioUIIngressDiscriminator extends LabelDiscriminator<Ingress> {

        public static ResourceDiscriminator<Ingress, ApicurioRegistry3> INSTANCE = new StudioUIIngressDiscriminator();

        public StudioUIIngressDiscriminator() {
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_STUDIO_UI
            ));
        }
    }

    public static class StudioUiPodDisruptionBudgetDiscriminator
            extends LabelDiscriminator<PodDisruptionBudget> {

        public static final ResourceDiscriminator<PodDisruptionBudget, ApicurioRegistry3> INSTANCE = new AppPodDisruptionBudgetDiscriminator();

        public StudioUiPodDisruptionBudgetDiscriminator() {
            // spotless:off
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_STUDIO_UI
            ));
            // spotless:on
        }
    }

}
