package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class KeycloakITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KeycloakITTest.class);

    @Test
    void testKeycloakPlain() {
        //Preparation, deploy Keycloak
        client.load(KeycloakITTest.class
                .getResourceAsStream("/k8s/examples/auth/keycloak.yaml")).create();
        await().ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments()
                    .withName("keycloak").get()
                    .getStatus().getReadyReplicas()).isEqualTo(1);
        });

        //Deploy Registry
        var registry = deserialize(
                "k8s/examples/auth/simple-with_keycloak.apicurioregistry3.yaml",
                ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);

        client.resource(registry).create();

        //Assertions, verify Registry deployment
        checkDeploymentExists(registry, COMPONENT_APP, 1);
        checkDeploymentExists(registry, COMPONENT_UI, 1);
        //FIXME: Check Registry deployment has the expected auth values.
    }
}
