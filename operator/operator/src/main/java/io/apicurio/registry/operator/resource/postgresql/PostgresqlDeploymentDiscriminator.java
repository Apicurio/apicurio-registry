package io.apicurio.registry.operator.resource.postgresql;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminator;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

import java.util.Map;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_POSTGRESQL;

public class PostgresqlDeploymentDiscriminator extends LabelDiscriminator<Deployment> {

    public static ResourceDiscriminator<Deployment, ApicurioRegistry3> INSTANCE = new PostgresqlDeploymentDiscriminator();

    public PostgresqlDeploymentDiscriminator() {
        // spotless:off
        super(Map.of(
                "app.kubernetes.io/name", "apicurio-registry",
                "app.kubernetes.io/component", COMPONENT_POSTGRESQL
        ));
        // spotless:on
    }
}
