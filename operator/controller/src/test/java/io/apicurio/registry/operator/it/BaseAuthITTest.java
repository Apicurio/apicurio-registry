package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class BaseAuthITTest extends ITBase {

    protected static ApicurioRegistry3 prepareInfra(String keycloakResource, String apicurioResource) {
        installKeycloak(keycloakResource);

        // Deploy Registry
        var registry = deserialize(apicurioResource, ApicurioRegistry3.class);

        registry.getMetadata().setNamespace(namespace);

        return registry;
    }

    protected static void installKeycloak(String keycloakResource) {
        List<HasMetadata> resources = Serialization
                .unmarshal(AuthITTest.class.getResourceAsStream(keycloakResource));

        createResources(resources, "Keycloak");

        await().ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments().withName("keycloak").get().getStatus().getReadyReplicas())
                    .isEqualTo(1);
        });

        createKeycloakDNSResolution("simple-keycloak.apps.cluster.example",
                "keycloak." + namespace + ".svc.cluster.local");

    }
}
