package io.apicurio.registry.operator.resource.ui;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminator;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;

public class UIIngressDiscriminator extends LabelDiscriminator<Ingress> {

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
