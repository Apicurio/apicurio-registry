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
    public static void initializeClientsDefaultRoles(ApicurioRegistry apicurioRegistry, String hostname) {
        // GET ADMIN API CLIENT
        // Create admin API client
        adminClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update admin API client with it
        adminClient.setToken(
                KeycloakUtils.getAccessToken(apicurioRegistry, Constants.SSO_ADMIN_USER, Constants.SSO_USER_PASSWORD)
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
                        Constants.SSO_DEVELOPER_USER,
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
                KeycloakUtils.getAccessToken(apicurioRegistry, Constants.SSO_READONLY_USER, Constants.SSO_USER_PASSWORD)
        );
        // Set authentication method to token for readonly client
        readonlyClient.setAuthMethod(AuthMethod.TOKEN);
    }

    public static void initializeClientsUserDefinedRoles(ApicurioRegistry apicurioRegistry, String hostname) {
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
    }

    public static void testRoleBasedAuthorizationRoleNames(ApicurioRegistry apicurioRegistry) {
        /* RUN PRE-TEST ACTIONS */

        // GET REGISTRY HOSTNAME
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Get registry hostname
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Basic environment variable to enable role based authorization
        EnvVar basicEnvVar = new EnvVar() {{
            setName("ROLE_BASED_AUTHZ_ENABLED");
            setValue("true");
        }};
        // Environment variables with default roles
        List<EnvVar> defaultRolesEnvVars = new ArrayList<>();
        // * Define name of admin role
        defaultRolesEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ROLES_ADMIN");
            setValue("sr-admin");
        }});
        // * Define name of developer role
        defaultRolesEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ROLES_DEVELOPER");
            setValue("sr-developer");
        }});
        // * Define name of readonly role
        defaultRolesEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ROLES_READONLY");
            setValue("sr-readonly");
        }});
        // Environment variables with user-defined roles
        List<EnvVar> userDefinedRolesEnvVars = new ArrayList<>();
        // * Define name of admin role
        userDefinedRolesEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ROLES_ADMIN");
            setValue("test-admin-role");
        }});
        // * Define name of developer role
        userDefinedRolesEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ROLES_DEVELOPER");
            setValue("test-developer-role");
        }});
        // * Define name of readonly role
        userDefinedRolesEnvVars.add(new EnvVar() {{
            setName("REGISTRY_AUTH_ROLES_READONLY");
            setValue("test-readonly-role");
        }});

        /* RUN TEST ACTIONS */

        // ENABLE ROLE BASED AUTHORIZATION BY TOKEN
        // Set variable ROLE_BASED_AUTHZ_ENABLED to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, basicEnvVar);
        // Initialize clients with default roles
        initializeClientsDefaultRoles(apicurioRegistry, hostname);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());

        // TEST: Default values (not in environment) + default role users
        // Run test actions
        testRoleBasedEnabled();

        // TEST: Default values (not in environment) + user-defined role users
        // Initialize clients with user-defined roles
        initializeClientsUserDefinedRoles(apicurioRegistry, hostname);
        // Run test actions
        testRoleBasedEnabledAllForbidden();


        // ENABLE ROLE BASED AUTHORIZATION BY TOKEN IN REGISTRY WITH DEFAULT ROLE NAMES AND TEST IT
        // Set necessary environment variables
        DeploymentUtils.createOrReplaceDeploymentEnvVars(deployment, defaultRolesEnvVars);
        // Initialize clients with default roles
        initializeClientsDefaultRoles(apicurioRegistry, hostname);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());

        // TEST: Default values in environment + default role users
        // Run test actions
        testRoleBasedEnabled();

        // TEST: Default values in environment + user-defined role users
        // Initialize clients with user-defined roles
        initializeClientsUserDefinedRoles(apicurioRegistry, hostname);
        // Run test actions
        testRoleBasedEnabledAllForbidden();


        // ENABLE ROLE BASED AUTHORIZATION BY TOKEN IN REGISTRY WITH USER-DEFINED ROLE NAMES AND TEST IT
        // Set necessary environment variables
        DeploymentUtils.createOrReplaceDeploymentEnvVars(deployment, userDefinedRolesEnvVars);
        // Initialize clients with default roles
        initializeClientsDefaultRoles(apicurioRegistry, hostname);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());

        // TEST: User-defined values in environment + default role users
        // Run test actions
        testRoleBasedEnabledAllForbidden();

        // TEST: User-defined values in environment + user-defined role users
        // Initialize clients with user-defined roles
        initializeClientsUserDefinedRoles(apicurioRegistry, hostname);
        // Run test actions
        testRoleBasedEnabled();
    }
}
