package io.apicurio.registry.systemtests.auth.features;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;

public abstract class RoleBasedAuthorizationAdminOverride extends RoleBasedAuthorization {
    public static void initializeClients(ApicurioRegistry apicurioRegistry, String hostname) {
        initializeClients(apicurioRegistry, hostname, "");
    }
    public static void initializeClients(ApicurioRegistry apicurioRegistry, String hostname, String adminSuffix) {
        // GET ADMIN API CLIENT
        // Create admin API client
        adminClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update admin API client with it
        adminClient.setToken(
                KeycloakUtils.getAccessToken(
                        apicurioRegistry,
                        Constants.SSO_ADMIN_USER + adminSuffix,
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
}
