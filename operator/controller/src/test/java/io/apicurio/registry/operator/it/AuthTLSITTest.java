package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthSpec;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class AuthTLSITTest extends BaseAuthITTest {

    private static final Logger log = LoggerFactory.getLogger(AuthTLSITTest.class);

    @BeforeAll
    public static void init() {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(60));
    }

    /**
     * In this test, Keycloak is deployed using a self-signed certificate with the hostname set to the ingress
     * value. TLS verification is enabled at the Apicurio Registry level, mounting the trustore into the
     * Quarkus application using the custom resource.
     */
    @Test
    void testAuthTlsVerification() {
        // Preparation, deploy Keycloak
        ApicurioRegistry3 registry = prepareInfra("k8s/examples/auth/simple-with_keycloak.apicurioregistry3.yaml",
                "keycloak_realm.yaml", "/k8s/examples/auth/keycloak_https.yaml"
        );
        AuthSpec authSpec = registry.getSpec().getApp().getAuth();

        Assertions.assertEquals("registry-api", authSpec.getAppClientId());
        Assertions.assertEquals("apicurio-registry", authSpec.getUiClientId());
        Assertions.assertEquals(true, authSpec.getEnabled());
        Assertions.assertEquals("https://simple-keycloak.apps.cluster.example/realms/registry",
                authSpec.getAuthServerUrl());
        Assertions.assertEquals("https://simple-ui.apps.cluster.example", authSpec.getRedirectURI());
        Assertions.assertEquals("https://simple-ui.apps.cluster.example", authSpec.getLogoutURL());

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
                .contains(EnvironmentVariables.OIDC_TLS_VERIFICATION + "=" + "required");
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
                .contains(EnvironmentVariables.OIDC_TLS_VERIFICATION + "=" + "required");
    }
}
