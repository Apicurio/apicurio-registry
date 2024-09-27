package io.apicurio.registry.operator.resource;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;

public class LabelDiscriminators {

    public static class AppDeploymentDiscriminator extends LabelDiscriminator<Deployment> {

        public static final ResourceDiscriminator<Deployment, ApicurioRegistry3> INSTANCE = new AppDeploymentDiscriminator();

        public AppDeploymentDiscriminator() {
            // spotless:off
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_APP
            ));
            // spotless:on
        }
    }

    public static class UIDeploymentDiscriminator extends LabelDiscriminator<Deployment> {

        public static final ResourceDiscriminator<Deployment, ApicurioRegistry3> INSTANCE = new UIDeploymentDiscriminator();

        public UIDeploymentDiscriminator() {
            // spotless:off
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_UI
            ));
            // spotless:on
        }
    }

    public static class AppServiceDiscriminator extends LabelDiscriminator<Service> {

        public static final ResourceDiscriminator<Service, ApicurioRegistry3> INSTANCE = new AppServiceDiscriminator();

        public AppServiceDiscriminator() {
            // spotless:off
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_APP
            ));
            // spotless:on
        }
    }

    public static class UIServiceDiscriminator extends LabelDiscriminator<Service> {

        public static ResourceDiscriminator<Service, ApicurioRegistry3> INSTANCE = new UIServiceDiscriminator();

        public UIServiceDiscriminator() {
            // spotless:off
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_UI
            ));
            // spotless:on
        }
    }

    public static class AppIngressDiscriminator extends LabelDiscriminator<Ingress> {

        public static final ResourceDiscriminator<Ingress, ApicurioRegistry3> INSTANCE = new AppIngressDiscriminator();

        public AppIngressDiscriminator() {
            // spotless:off
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_APP
            ));
            // spotless:on
        }
    }

    public static class UIIngressDiscriminator extends LabelDiscriminator<Ingress> {

        public static ResourceDiscriminator<Ingress, ApicurioRegistry3> INSTANCE = new UIIngressDiscriminator();

        public UIIngressDiscriminator() {
            // spotless:off
            super(Map.of(
                    "app.kubernetes.io/name", "apicurio-registry",
                    "app.kubernetes.io/component", COMPONENT_UI
            ));
            // spotless:on
        }
    }
}
