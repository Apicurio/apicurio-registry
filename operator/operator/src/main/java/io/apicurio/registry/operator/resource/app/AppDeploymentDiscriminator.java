package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminator;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;

public class AppDeploymentDiscriminator extends LabelDiscriminator<Deployment> {

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
