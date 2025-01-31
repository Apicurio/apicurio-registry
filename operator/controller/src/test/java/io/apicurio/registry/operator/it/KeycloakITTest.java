package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class KeycloakITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(KeycloakITTest.class);

    @BeforeAll
    public static void init() {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(60));
    }

    /**
     * In this test, Keycloak is deployed using a self-signed certificate with the hostname set to the ingress
     * value. TLS verification is disabled at the Apicurio Registry level, so even in that case the deployment
     * works.
     */
    @Test
    void testKeycloakPlain() {
        // Preparation, deploy Keycloak
        List<HasMetadata> resources = Serialization
                .unmarshal(KeycloakITTest.class.getResourceAsStream("/k8s/examples/auth/keycloak.yaml"));

        createResources(resources, "Keycloak");

        await().ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments().withName("keycloak").get().getStatus().getReadyReplicas())
                    .isEqualTo(1);
        });

        createKeycloakDNSResolution("simple-keycloak.apps.cluster.example",
                "keycloak." + namespace + ".svc.cluster.local");

        // Deploy Registry
        var registry = deserialize("k8s/examples/auth/simple-with_keycloak.apicurioregistry3.yaml",
                ApicurioRegistry3.class);

        registry.getMetadata().setNamespace(namespace);

        var appAuthSpec = registry.getSpec().getApp().getAuth();

        Assertions.assertEquals("registry-api", appAuthSpec.getAppClientId());
        Assertions.assertEquals("apicurio-registry", appAuthSpec.getUiClientId());
        Assertions.assertEquals(true, appAuthSpec.getEnabled());
        Assertions.assertEquals("https://simple-keycloak.apps.cluster.example/realms/registry",
                appAuthSpec.getAuthServerUrl());
        Assertions.assertEquals("https://simple-ui.apps.cluster.example", appAuthSpec.getRedirectURI());
        Assertions.assertEquals("https://simple-ui.apps.cluster.example", appAuthSpec.getLogoutURL());

        client.resource(registry).create();

        // Assertions, checks registry deployments exist
        checkDeploymentExists(registry, COMPONENT_APP, 1);
        checkDeploymentExists(registry, COMPONENT_UI, 1);

        // App deployment auth related assertions
        var appEnv = getContainerFromDeployment(
                client.apps().deployments().inNamespace(namespace)
                        .withName(registry.getMetadata().getName() + "-app-deployment").get(),
                REGISTRY_APP_CONTAINER_NAME).getEnv();

        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_REGISTRY_AUTH_ENABLED + "=" + "true");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.OIDC_TLS_VERIFICATION + "=" + "none");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_REGISTRY_APP_CLIENT_ID + "=" + "registry-api");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_REGISTRY_UI_CLIENT_ID + "=" + "apicurio-registry");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_REGISTRY_AUTH_SERVER_URL + "="
                        + "https://simple-keycloak.apps.cluster.example/realms/registry");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_UI_AUTH_OIDC_REDIRECT_URI + "="
                        + "https://simple-ui.apps.cluster.example");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_UI_AUTH_OIDC_LOGOUT_URL + "="
                        + "https://simple-ui.apps.cluster.example");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.OIDC_TLS_VERIFICATION + "=" + "none");
    }
}
