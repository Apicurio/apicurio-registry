package io.apicurio.registry.operator.resource.ui;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminator;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;

public class UIDeploymentDiscriminator extends LabelDiscriminator<Deployment> {

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
