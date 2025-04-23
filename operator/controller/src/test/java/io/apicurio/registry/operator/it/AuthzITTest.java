package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.auth.AdminOverrideSpec;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthSpec;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthzSpec;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class AuthzITTest extends BaseAuthITTest {

    /**
     * In this test, Keycloak is deployed using a self-signed certificate with the hostname set to the ingress
     * value. TLS verification is disabled at the Apicurio Registry level, so even in that case the deployment
     * works.
     */
    @Test
    void testAuthz() {
        // Preparation, deploy Keycloak
        // Preparation, deploy Keycloak
        ApicurioRegistry3 registry = prepareInfra("k8s/examples/auth/authz-with_keycloak.apicurioregistry3.yaml",
                "/k8s/examples/auth/keycloak_realm.yaml", "/k8s/examples/auth/keycloak_https.yaml"
        );
        AuthSpec authSpec = registry.getSpec().getApp().getAuth();

        Assertions.assertEquals("registry-api", authSpec.getAppClientId());
        Assertions.assertEquals("apicurio-registry", authSpec.getUiClientId());
        Assertions.assertEquals(true, authSpec.getEnabled());
        Assertions.assertEquals("https://simple-keycloak.apps.cluster.example/realms/registry",
                authSpec.getAuthServerUrl());
        Assertions.assertEquals("https://simple-ui.apps.cluster.example", authSpec.getRedirectUri());
        Assertions.assertEquals("https://simple-ui.apps.cluster.example", authSpec.getLogoutUrl());

        AuthzSpec authzSpec = authSpec.getAuthz();

        // Authz exclusive assertions
        Assertions.assertEquals(true, authzSpec.getEnabled());
        Assertions.assertEquals(true, authzSpec.getOwnerOnlyEnabled());
        Assertions.assertEquals(true, authzSpec.getGroupAccessEnabled());
        Assertions.assertEquals(true, authzSpec.getReadAccessEnabled());
        Assertions.assertEquals("token", authzSpec.getRoleSource());
        Assertions.assertEquals("admin", authzSpec.getAdminRole());
        Assertions.assertEquals("dev", authzSpec.getDeveloperRole());
        Assertions.assertEquals("read", authzSpec.getReadOnlyRole());

        // Admin Override assertions
        AdminOverrideSpec adminOverrideSpec = authzSpec.getAdminOverride();
        Assertions.assertEquals(true, adminOverrideSpec.getEnabled());
        Assertions.assertEquals("token", adminOverrideSpec.getFrom());
        Assertions.assertEquals("claim", adminOverrideSpec.getType());
        Assertions.assertEquals("admin", adminOverrideSpec.getRole());
        Assertions.assertEquals("test", adminOverrideSpec.getClaimName());
        Assertions.assertEquals("test", adminOverrideSpec.getClaimValue());

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

        // Authz exclusive assertions
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ROLE_BASED_AUTHORIZATION + "=" + "true");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue()).contains(
                EnvironmentVariables.APICURIO_AUTH_AUTHENTICATED_READ_ACCESS_ENABLED + "=" + "true");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_OWNER_ONLY_AUTHORIZATION_LIMIT_GROUP_ACCESS + "="
                        + "true");

        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_OWNER_ONLY_AUTHORIZATION + "=" + "true");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ROLE_SOURCE + "=" + "token");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ROLES_ADMIN + "=" + "admin");

        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ROLES_DEVELOPER + "=" + "dev");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ROLES_READONLY + "=" + "read");

        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_ENABLED + "=" + "true");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_FROM + "=" + "token");
        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_TYPE + "=" + "claim");

        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_ROLE + "=" + "admin");

        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_CLAIM + "=" + "test");

        assertThat(appEnv).map(ev -> ev.getName() + "=" + ev.getValue())
                .contains(EnvironmentVariables.APICURIO_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE + "=" + "test");
    }
}
