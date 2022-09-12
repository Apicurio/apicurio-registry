package io.apicurio.registry.systemtests.auth.features;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ApicurioRegistryUserRole;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.DeploymentUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

public class RoleBasedAuthorizationApplication extends RoleBasedAuthorization {
    public static void testRoleBasedAuthorizationApplication(ApicurioRegistry apicurioRegistry) {
        /* RUN PRE-TEST ACTIONS */

        // GET REGISTRY HOSTNAME
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Get registry hostname
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // GET SUPER ADMIN API CLIENT (used for registry configuration)
        // Create super admin API client
        superAdminClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update super admin API client with it
        superAdminClient.setToken(
                // Use admin user defined in Keycloak as control user
                KeycloakUtils.getAccessToken(apicurioRegistry, Constants.SSO_ADMIN_USER, Constants.SSO_USER_PASSWORD)
        );
        // Set authentication method to token for super admin client
        superAdminClient.setAuthMethod(AuthMethod.TOKEN);

        // GET ADMIN API CLIENT (used for internal admin role)
        // Create admin API client
        adminClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update admin API client with it
        adminClient.setToken(
                // Use no-role user as an admin of registry
                KeycloakUtils.getAccessToken(apicurioRegistry, Constants.SSO_NO_ROLE_USER, Constants.SSO_USER_PASSWORD)
        );
        // Set authentication method to token for admin client
        adminClient.setAuthMethod(AuthMethod.TOKEN);

        // GET DEVELOPER API CLIENT (used for internal developer role)
        // Create developer API client
        developerClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update developer API client with it
        developerClient.setToken(
                // Use readonly user as a developer of registry
                KeycloakUtils.getAccessToken(apicurioRegistry, Constants.SSO_READONLY_USER, Constants.SSO_USER_PASSWORD)
        );
        // Set authentication method to token for developer client
        developerClient.setAuthMethod(AuthMethod.TOKEN);

        // GET READONLY API CLIENT (used for internal readonly role)
        // Create readonly API client
        readonlyClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update readonly API client with it
        readonlyClient.setToken(
                KeycloakUtils.getAccessToken(
                        apicurioRegistry,
                        // User developer user as a readonly in registry
                        Constants.SSO_DEVELOPER_USER,
                        Constants.SSO_USER_PASSWORD
                )
        );
        // Set authentication method to token for readonly client
        readonlyClient.setAuthMethod(AuthMethod.TOKEN);

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Prepare environment variables for test
        List<EnvVar> testEnvVars = new ArrayList<>();
        // Enable role based authorization
        testEnvVars.add(new EnvVar() {{
            setName("ROLE_BASED_AUTHZ_ENABLED");
            setValue("true");
        }});
        // Set authorization source to application
        testEnvVars.add(new EnvVar() {{
            setName("ROLE_BASED_AUTHZ_SOURCE");
            setValue("application");
        }});
        // Enable admin override to use super admin client as registry admin
        testEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ADMIN_OVERRIDE_ENABLED");
            setValue("true");
        }});

        // WAIT FOR API AVAILABILITY
        Assertions.assertTrue(superAdminClient.waitServiceAvailable());

        /* RUN TEST ACTIONS */

        // ENABLE ROLE BASED AUTHORIZATION BY APPLICATION IN REGISTRY AND TEST IT
        // Set necessary environment variables of deployment to enable this feature
        DeploymentUtils.createOrReplaceDeploymentEnvVars(deployment, testEnvVars);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Create role for admin user
        superAdminClient.createUser(Constants.SSO_NO_ROLE_USER, ApicurioRegistryUserRole.ADMIN, "admin");
        // Create role for developer user
        superAdminClient.createUser(Constants.SSO_READONLY_USER, ApicurioRegistryUserRole.DEVELOPER, "developer");
        // Create role for readonly user
        superAdminClient.createUser(Constants.SSO_DEVELOPER_USER, ApicurioRegistryUserRole.READ_ONLY, "readonly");

        // Run test actions
        testRoleBasedEnabled();
    }
}
