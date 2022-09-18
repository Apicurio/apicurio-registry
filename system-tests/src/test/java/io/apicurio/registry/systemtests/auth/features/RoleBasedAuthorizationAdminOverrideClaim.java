package io.apicurio.registry.systemtests.auth.features;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DeploymentUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.jupiter.api.Assertions;

public class RoleBasedAuthorizationAdminOverrideClaim extends RoleBasedAuthorizationAdminOverride {
    public static void testRoleBasedAuthorizationAdminOverrideRole(ApicurioRegistry apicurioRegistry) {
        /* RUN PRE-TEST ACTIONS */

        // GET REGISTRY HOSTNAME
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Get registry hostname
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // INITIALIZE API CLIENTS
        // Initialize API clients with default roles
        initializeClients(apicurioRegistry, hostname);

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Basic environment variable to enable/disable role based authorization
        EnvVar roleBasedAuth = new EnvVar() {{
            setName("ROLE_BASED_AUTHZ_ENABLED");
            setValue("true");
        }};
        // Environment variable to set authorization source to application
        EnvVar roleBasedAuthSource = new EnvVar() {{
            setName("ROLE_BASED_AUTHZ_SOURCE");
            setValue("application");
        }};
        // Environment variable to enable/disable admin override
        EnvVar roleBasedAuthAdminOverride = new EnvVar() {{
            setName("REGISTRY_AUTH_ADMIN_OVERRIDE_ENABLED");
            setValue("true");
        }};
        // Environment variable to set admin override information source
        EnvVar roleBasedAuthAdminOverrideSource = new EnvVar() {{
            setName("REGISTRY_AUTH_ADMIN_OVERRIDE_FROM");
            setValue("token");
        }};
        // Set the type of information used to determine if a user is an admin
        EnvVar roleBasedAuthAdminOverrideType = new EnvVar() {{
            setName("REGISTRY_AUTH_ADMIN_OVERRIDE_TYPE");
            setValue("role");
        }};
        // Set name of admin override claim
        EnvVar roleBasedAuthAdminOverrideClaim = new EnvVar() {{
            setName("REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM");
            setValue("org-admin");
        }};
        // Set value of admin override claim
        EnvVar roleBasedAuthAdminOverrideClaimValue = new EnvVar() {{
            setName("REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE");
            setValue("true");
        }};

        // WAIT FOR API AVAILABILITY
        Assertions.assertTrue(adminClient.waitServiceAvailable());

        /* RUN TEST ACTIONS */

        // DO NOT SET ANY ROLE BASED RELATED ENVIRONMENT VARIABLE AND TEST DEFAULT BEHAVIOR
        // Run test actions
        testRoleBasedDisabled();

        // ENABLE ROLE BASED AUTHORIZATION BY TOKEN IN REGISTRY AND TEST IT
        // Set environment variable ROLE_BASED_AUTHZ_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuth);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions
        testRoleBasedEnabled();

        // REINITIALIZE API CLIENTS TO "REFRESH" TOKENS
        initializeClients(apicurioRegistry, hostname);

        // SET ROLE BASED AUTHORIZATION SOURCE IN REGISTRY TO APPLICATION AND TEST IT
        // Set environment variable ROLE_BASED_AUTHZ_SOURCE of deployment to application
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthSource);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions
        testRoleBasedEnabledAllForbidden();

        // DISABLE ADMIN OVERRIDE FEATURE IN REGISTRY AND TEST IT (SET TO DEFAULT VALUE)
        // Update environment variable value to false
        roleBasedAuthAdminOverride.setValue("false");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_ENABLED of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverride);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions
        testRoleBasedEnabledAllForbidden();

        // ENABLE ADMIN OVERRIDE FEATURE IN REGISTRY AND TEST IT
        // Update environment variable value to true
        roleBasedAuthAdminOverride.setValue("true");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverride);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions
        testRoleBasedEnabledOnlyAdminAllowed();

        // SET ADMIN OVERRIDE INFORMATION SOURCE IN REGISTRY TO TOKEN AND TEST IT (SET TO DEFAULT VALUE)
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_FROM of deployment to token
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideSource);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions
        testRoleBasedEnabledOnlyAdminAllowed();

        // SET ADMIN OVERRIDE INFORMATION SOURCE IN REGISTRY TO ? AND TEST IT
        // NOTE: Nothing to set here, only token is currently supported.

        // SET ADMIN OVERRIDE INFORMATION TYPE IN REGISTRY TO CLAIM AND TEST IT
        // claim = default (org-admin), value = default (true)
        // Update environment variable value to claim
        roleBasedAuthAdminOverrideType.setValue("claim");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_TYPE of deployment to claim
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideType);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions with default clients
        testRoleBasedEnabledAllForbidden();
        // Initialize clients with admin using default claim values org-admin=true
        initializeClients(apicurioRegistry, hostname, "-org-admin-true");
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledOnlyAdminAllowed();

        // SET ADMIN OVERRIDE CLAIM NAME IN REGISTRY TO org-admin AND TEST IT (SET TO DEFAULT VALUE)
        // claim = org-admin, value = default (true)
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM of deployment to org-admin
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaim);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledOnlyAdminAllowed();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO true AND TEST IT (SET TO DEFAULT VALUE)
        // claim = org-admin, value = true
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledOnlyAdminAllowed();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO yes AND TEST IT
        // claim = org-admin, value = yes
        // Update environment variable value to yes
        roleBasedAuthAdminOverrideClaimValue.setValue("yes");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to yes
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Initialize clients with admin using claim values org-admin=yes
        initializeClients(apicurioRegistry, hostname, "-org-admin-yes");
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledOnlyAdminAllowed();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO invalid AND TEST IT
        // claim = org-admin, value = invalid
        // Update environment variable value to invalid
        roleBasedAuthAdminOverrideClaimValue.setValue("invalid");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to invalid
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledAllForbidden();

        // SET ADMIN OVERRIDE CLAIM NAME IN REGISTRY TO admin-role AND TEST IT
        // claim = admin-role, value = invalid
        // Update environment variable value to admin-role
        roleBasedAuthAdminOverrideClaim.setValue("admin-role");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM of deployment to admin-role
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaim);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Initialize clients with admin using claim values admin-role=true
        initializeClients(apicurioRegistry, hostname, "-admin-role-true");
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledAllForbidden();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO true AND TEST IT (SET TO DEFAULT VALUE)
        // claim = admin-role, value = true
        // Update environment variable value to true
        roleBasedAuthAdminOverrideClaimValue.setValue("true");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledOnlyAdminAllowed();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO admin AND TEST IT
        // claim = admin-role, value = admin
        // Update environment variable value to admin
        roleBasedAuthAdminOverrideClaimValue.setValue("admin");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to yes
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Initialize clients with admin using claim values admin-role=admin
        initializeClients(apicurioRegistry, hostname, "-admin-role-admin");
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledOnlyAdminAllowed();

        // REMOVE ADMIN OVERRIDE CLAIM VALUE IN REGISTRY AND TEST IT (DEFAULTS TO true)
        // claim = admin-role, value = default (true)
        // Remove environment variable
        DeploymentUtils.deleteDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue.getName());
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Initialize clients with admin using claim values admin-role=true
        initializeClients(apicurioRegistry, hostname, "-admin-role-true");
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledOnlyAdminAllowed();

        // SET ADMIN OVERRIDE CLAIM NAME IN REGISTRY TO invalid AND TEST IT
        // claim = invalid, value = default (true)
        // Update environment variable value to invalid
        roleBasedAuthAdminOverrideClaim.setValue("invalid");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM of deployment to invalid
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaim);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Initialize clients with admin using claim values org-admin=true
        initializeClients(apicurioRegistry, hostname, "-org-admin-true");
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledAllForbidden();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO true AND TEST IT
        // claim = invalid, value = true
        // Update environment variable value to true
        roleBasedAuthAdminOverrideClaimValue.setValue("true");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledAllForbidden();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO yes AND TEST IT
        // claim = invalid, value = yes
        // Update environment variable value to yes
        roleBasedAuthAdminOverrideClaimValue.setValue("yes");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to yes
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Initialize clients with admin using claim values org-admin=yes
        initializeClients(apicurioRegistry, hostname, "-org-admin-yes");
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledAllForbidden();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO invalid AND TEST IT
        // claim = invalid, value = invalid
        // Update environment variable value to invalid
        roleBasedAuthAdminOverrideClaimValue.setValue("invalid");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to invalid
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledAllForbidden();

        // REMOVE ADMIN OVERRIDE CLAIM NAME IN REGISTRY AND TEST IT (DEFAULTS TO org-admin)
        // claim = default (org-admin), value = invalid
        // Remove environment variable
        DeploymentUtils.deleteDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaim.getName());
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledAllForbidden();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO yes AND TEST IT
        // claim = default (org-admin), value = yes
        // Update environment variable value to yes
        roleBasedAuthAdminOverrideClaimValue.setValue("yes");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to yes
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledOnlyAdminAllowed();

        // SET ADMIN OVERRIDE CLAIM VALUE IN REGISTRY TO true AND TEST IT
        // claim = default (org-admin), value = true
        // Update environment variable value to true
        roleBasedAuthAdminOverrideClaimValue.setValue("true");
        // Set environment variable REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, roleBasedAuthAdminOverrideClaimValue);
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // Initialize clients with admin using claim values org-admin=true
        initializeClients(apicurioRegistry, hostname, "-org-admin-true");
        // Run test actions with user-defined admin claim (other clients are still default)
        testRoleBasedEnabledOnlyAdminAllowed();
    }
}
