package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminator;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;

public class AppServiceDiscriminator extends LabelDiscriminator<Service> {

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
