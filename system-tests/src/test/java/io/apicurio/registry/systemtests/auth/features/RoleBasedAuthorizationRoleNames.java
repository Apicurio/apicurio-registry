package io.apicurio.registry.systemtests.auth.features;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
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

public class RoleBasedAuthorizationRoleNames extends RoleBasedAuthorization {
    public static void testRoleBasedAuthorizationRoleNames(ApicurioRegistry apicurioRegistry) {
        /* RUN PRE-TEST ACTIONS */

        // GET REGISTRY HOSTNAME
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Get registry hostname
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // DEFINE TEST USER SUFFIX
        // Define suffix for test users that use user-defined role names
        String userSuffix = "-role";

        // GET ADMIN API CLIENT
        // Create admin API client
        adminClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update admin API client with it
        adminClient.setToken(
                KeycloakUtils.getAccessToken(
                        apicurioRegistry,
                        Constants.SSO_ADMIN_USER + userSuffix,
                        Constants.SSO_USER_PASSWORD
                )
        );
        // Set authentication method to token for admin client
        adminClient.setAuthMethod(AuthMethod.TOKEN);

        // GET DEVELOPER API CLIENT
        // Create developer API client
        developerClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update developer API client with it
        developerClient.setToken(
                KeycloakUtils.getAccessToken(
                        apicurioRegistry,
                        Constants.SSO_DEVELOPER_USER + userSuffix,
                        Constants.SSO_USER_PASSWORD
                )
        );
        // Set authentication method to token for developer client
        developerClient.setAuthMethod(AuthMethod.TOKEN);

        // GET READONLY API CLIENT
        // Create readonly API client
        readonlyClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update readonly API client with it
        readonlyClient.setToken(
                KeycloakUtils.getAccessToken(
                        apicurioRegistry,
                        Constants.SSO_READONLY_USER + userSuffix,
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
        // Define name of admin role
        testEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ROLES_ADMIN");
            setValue("test-admin-role");
        }});
        // Define name of developer role
        testEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ROLES_DEVELOPER");
            setValue("test-developer-role");
        }});
        // Define name of readonly role
        testEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ROLES_READONLY");
            setValue("test-readonly-role");
        }});

        // WAIT FOR API AVAILABILITY
        Assertions.assertTrue(adminClient.waitServiceAvailable());

        /* RUN TEST ACTIONS */

        // ENABLE ROLE BASED AUTHORIZATION BY TOKEN IN REGISTRY WITH USER-DEFINED ROLE NAMES AND TEST IT
        // Set necessary environment variables
        DeploymentUtils.createOrReplaceDeploymentEnvVars(deployment, testEnvVars);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions
        testRoleBasedEnabled();
    }
}
