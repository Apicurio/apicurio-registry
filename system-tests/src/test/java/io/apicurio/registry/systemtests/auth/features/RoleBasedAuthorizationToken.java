package io.apicurio.registry.systemtests.auth.features;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.client.AuthMethod;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.CompatibilityLevel;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.DeploymentUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.framework.ValidityLevel;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;

public class RoleBasedAuthorizationToken {
    public static void testRoleBasedAuthorizationToken(ApicurioRegistry apicurioRegistry) {
        /* RUN PRE-TEST ACTIONS */

        // GET REGISTRY HOSTNAME
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Get registry hostname
        String hostname = ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry);

        // GET ADMIN API CLIENT
        // Create admin API client
        ApicurioRegistryApiClient adminClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update admin API client with it
        adminClient.setToken(
                KeycloakUtils.getAccessToken(apicurioRegistry, Constants.SSO_ADMIN_USER, Constants.SSO_USER_PASSWORD)
        );
        // Set authentication method to token for admin client
        adminClient.setAuthMethod(AuthMethod.TOKEN);

        // GET DEVELOPER API CLIENT
        // Create developer API client
        ApicurioRegistryApiClient developerClient = new ApicurioRegistryApiClient(hostname);
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
        ApicurioRegistryApiClient readonlyClient = new ApicurioRegistryApiClient(hostname);
        // Get access token from Keycloak and update readonly API client with it
        readonlyClient.setToken(
                KeycloakUtils.getAccessToken(apicurioRegistry, Constants.SSO_READONLY_USER, Constants.SSO_USER_PASSWORD)
        );
        // Set authentication method to token for readonly client
        readonlyClient.setAuthMethod(AuthMethod.TOKEN);

        // WAIT FOR API AVAILABILITY
        Assertions.assertTrue(adminClient.waitServiceAvailable());

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        Deployment deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Define artifact group ID for all users
        String groupId = "roleBasedAuthorizationTokenTest";
        // Define artifact ID prefix for all users
        String id = "role-based-authorization-token-test";
        // Define artifact ID for admin user
        String adminId = id + "-admin";
        // Define artifact ID for developer user
        String developerId = id + "-developer";
        // Define artifact ID for readonly user
        String readonlyId = id + "-readonly";
        // Define artifact ID suffix for second artifact of the same user
        String secondId = "-second";
        // Define artifact ID suffix for third artifact of the same user
        String thirdId = "-third";
        // Define artifact type for all artifacts
        ArtifactType type = ArtifactType.JSON;
        // Define artifact initial content for all artifacts
        String initialContent = "{}";
        // Define artifact updated content for all artifacts
        String updatedContent = "{\"key\":\"id\"}";
        // Define second artifact updated content for all artifacts
        String secondUpdatedContent = "{\"id\":\"key\"}";
        // Define third artifact updated content for all artifacts
        String thirdUpdatedContent = "{\"key\":\"value\"}";

        /* RUN TEST ACTIONS */

        // TEST DEFAULT VALUE OF ROLE BASED AUTHORIZATION BY TOKEN (false)
        // TODO: Add artifact rules
        // --- global validity rule by admin
        // Check that API returns 204 No Content when enabling global validity rule by admin
        Assertions.assertTrue(adminClient.enableGlobalValidityRule());
        // Check that API returns 200 OK when listing global rules by admin
        Assertions.assertNotNull(adminClient.listGlobalRules());
        // Check that API returns 200 OK when updating global validity rule by admin
        Assertions.assertTrue(adminClient.updateGlobalValidityRule(ValidityLevel.SYNTAX_ONLY));
        // Check that API returns 200 OK when getting global validity rule by admin
        Assertions.assertNotNull(adminClient.getGlobalValidityRule());
        // Check that API returns 204 No Content when disabling global validity rule by admin
        Assertions.assertTrue(adminClient.disableGlobalValidityRule());
        // --- global validity rule by developer
        // Check that API returns 204 No Content when enabling global validity rule by developer
        Assertions.assertTrue(developerClient.enableGlobalValidityRule());
        // Check that API returns 200 OK when listing global rules by developer
        Assertions.assertNotNull(developerClient.listGlobalRules());
        // Check that API returns 200 OK when updating global validity rule by developer
        Assertions.assertTrue(developerClient.updateGlobalValidityRule(ValidityLevel.SYNTAX_ONLY));
        // Check that API returns 200 OK when getting global validity rule by developer
        Assertions.assertNotNull(developerClient.getGlobalValidityRule());
        // Check that API returns 204 No Content when disabling global validity rule by developer
        Assertions.assertTrue(developerClient.disableGlobalValidityRule());
        // --- global validity rule by readonly
        // Check that API returns 204 No Content when enabling global validity rule by developer
        Assertions.assertTrue(readonlyClient.enableGlobalValidityRule());
        // Check that API returns 200 OK when listing global rules by developer
        Assertions.assertNotNull(readonlyClient.listGlobalRules());
        // Check that API returns 200 OK when updating global validity rule by developer
        Assertions.assertTrue(readonlyClient.updateGlobalValidityRule(ValidityLevel.SYNTAX_ONLY));
        // Check that API returns 200 OK when getting global validity rule by developer
        Assertions.assertNotNull(readonlyClient.getGlobalValidityRule());
        // Check that API returns 204 No Content when disabling global validity rule by developer
        Assertions.assertTrue(readonlyClient.disableGlobalValidityRule());
        // --- global compatibility rule by admin
        // Check that API returns 204 No Content when enabling global compatibility rule by admin
        Assertions.assertTrue(adminClient.enableGlobalCompatibilityRule());
        // Check that API returns 200 OK when listing global rules by admin
        Assertions.assertNotNull(adminClient.listGlobalRules());
        // Check that API returns 200 OK when updating global compatibility rule by admin
        Assertions.assertTrue(adminClient.updateGlobalCompatibilityRule(CompatibilityLevel.BACKWARD_TRANSITIVE));
        // Check that API returns 200 OK when getting global compatibility rule by admin
        Assertions.assertNotNull(adminClient.getGlobalCompatibilityRule());
        // Check that API returns 204 No Content when disabling global compatibility rule by admin
        Assertions.assertTrue(adminClient.disableGlobalCompatibilityRule());
        // --- global compatibility rule by developer
        // Check that API returns 204 No Content when enabling global compatibility rule by developer
        Assertions.assertTrue(developerClient.enableGlobalCompatibilityRule());
        // Check that API returns 200 OK when listing global rules by developer
        Assertions.assertNotNull(developerClient.listGlobalRules());
        // Check that API returns 200 OK when updating global compatibility rule by developer
        Assertions.assertTrue(developerClient.updateGlobalCompatibilityRule(CompatibilityLevel.BACKWARD_TRANSITIVE));
        // Check that API returns 200 OK when getting global compatibility rule by developer
        Assertions.assertNotNull(developerClient.getGlobalCompatibilityRule());
        // Check that API returns 204 No Content when disabling global compatibility rule by developer
        Assertions.assertTrue(developerClient.disableGlobalCompatibilityRule());
        // --- global compatibility rule by readonly
        // Check that API returns 204 No Content when enabling global compatibility rule by developer
        Assertions.assertTrue(readonlyClient.enableGlobalCompatibilityRule());
        // Check that API returns 200 OK when listing global rules by developer
        Assertions.assertNotNull(readonlyClient.listGlobalRules());
        // Check that API returns 200 OK when updating global compatibility rule by developer
        Assertions.assertTrue(readonlyClient.updateGlobalCompatibilityRule(CompatibilityLevel.BACKWARD_TRANSITIVE));
        // Check that API returns 200 OK when getting global compatibility rule by developer
        Assertions.assertNotNull(readonlyClient.getGlobalCompatibilityRule());
        // Check that API returns 204 No Content when disabling global compatibility rule by developer
        Assertions.assertTrue(readonlyClient.disableGlobalCompatibilityRule());
        // --- list action
        // Check that API returns 200 OK when listing artifacts by admin
        Assertions.assertNotNull(adminClient.listArtifacts());
        // Check that API returns 200 OK when listing artifacts by developer
        Assertions.assertNotNull(developerClient.listArtifacts());
        // Check that API returns 200 OK when listing artifacts by readonly
        Assertions.assertNotNull(readonlyClient.listArtifacts());
        // --- create action
        // Check that API returns 200 OK when creating artifact by admin
        Assertions.assertTrue(adminClient.createArtifact(groupId, adminId, type, initialContent));
        // Check that API returns 200 OK when creating artifact by developer
        Assertions.assertTrue(developerClient.createArtifact(groupId, developerId, type, initialContent));
        // Check that API returns 200 OK when creating artifact by readonly
        Assertions.assertTrue(readonlyClient.createArtifact(groupId, readonlyId, type, initialContent));
        // Check that API returns 200 OK when creating second artifact by admin
        Assertions.assertTrue(adminClient.createArtifact(groupId, adminId + secondId, type, initialContent));
        // Check that API returns 200 OK when creating second artifact by developer
        Assertions.assertTrue(developerClient.createArtifact(groupId, developerId + secondId, type, initialContent));
        // Check that API returns 200 OK when creating artifact by readonly
        Assertions.assertTrue(readonlyClient.createArtifact(groupId, readonlyId + secondId, type, initialContent));
        // Check that API returns 200 OK when creating third artifact by admin
        Assertions.assertTrue(adminClient.createArtifact(groupId, adminId + thirdId, type, initialContent));
        // Check that API returns 200 OK when creating third artifact by developer
        Assertions.assertTrue(developerClient.createArtifact(groupId, developerId + thirdId, type, initialContent));
        // Check that API returns 200 OK when creating third artifact by readonly
        Assertions.assertTrue(readonlyClient.createArtifact(groupId, readonlyId + thirdId, type, initialContent));
        // --- list action on group
        // Check that API returns 200 OK when listing group artifacts by admin
        Assertions.assertNotNull(adminClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when listing group artifacts by developer
        Assertions.assertNotNull(developerClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when listing group artifacts by readonly
        Assertions.assertNotNull(readonlyClient.listGroupArtifacts(groupId));
        // --- read action on own artifact
        // Check that API returns 200 OK when reading artifact by admin
        Assertions.assertNotNull(adminClient.readArtifactContent(groupId, adminId));
        // Check that API returns 200 OK when reading artifact by developer
        Assertions.assertNotNull(developerClient.readArtifactContent(groupId, developerId));
        // Check that API returns 200 OK when reading artifact by readonly
        Assertions.assertNotNull(readonlyClient.readArtifactContent(groupId, readonlyId));
        // --- read action on another artifact
        // Check that API returns 200 OK when reading developer artifact by admin
        Assertions.assertNotNull(adminClient.readArtifactContent(groupId, developerId));
        // Check that API returns 200 OK when reading readonly artifact by admin
        Assertions.assertNotNull(adminClient.readArtifactContent(groupId, readonlyId));
        // Check that API returns 200 OK when reading admin artifact by developer
        Assertions.assertNotNull(developerClient.readArtifactContent(groupId, adminId));
        // Check that API returns 200 OK when reading readonly artifact by developer
        Assertions.assertNotNull(developerClient.readArtifactContent(groupId, readonlyId));
        // Check that API returns 200 OK when reading admin artifact by readonly
        Assertions.assertNotNull(readonlyClient.readArtifactContent(groupId, adminId));
        // Check that API returns 200 OK when reading developer artifact by readonly
        Assertions.assertNotNull(readonlyClient.readArtifactContent(groupId, developerId));
        // --- update action on own artifact
        // Check that API returns 200 OK when updating admin artifact by admin
        Assertions.assertTrue(adminClient.updateArtifact(groupId, adminId, updatedContent));
        // Check that API returns 200 OK when updating developer artifact by developer
        Assertions.assertTrue(developerClient.updateArtifact(groupId, developerId, updatedContent));
        // Check that API returns 200 OK when updating readonly artifact by readonly
        Assertions.assertTrue(readonlyClient.updateArtifact(groupId, readonlyId, updatedContent));
        // --- update action on another artifact
        // Check that API returns 200 OK when updating developer artifact by admin
        Assertions.assertTrue(adminClient.updateArtifact(groupId, developerId, secondUpdatedContent));
        // Check that API returns 200 OK when updating readonly artifact by admin
        Assertions.assertTrue(adminClient.updateArtifact(groupId, readonlyId, secondUpdatedContent));
        // Check that API returns 200 OK when updating admin artifact by developer
        Assertions.assertTrue(developerClient.updateArtifact(groupId, adminId, secondUpdatedContent));
        // Check that API returns 200 OK when updating readonly artifact by developer
        Assertions.assertTrue(developerClient.updateArtifact(groupId, readonlyId, thirdUpdatedContent));
        // Check that API returns 200 OK when updating admin artifact by readonly
        Assertions.assertTrue(readonlyClient.updateArtifact(groupId, adminId, thirdUpdatedContent));
        // Check that API returns 200 OK when updating developer artifact by readonly
        Assertions.assertTrue(readonlyClient.updateArtifact(groupId, developerId, thirdUpdatedContent));
        // --- delete action on own artifact
        // Check that API returns 204 No Content when deleting artifact by admin
        Assertions.assertTrue(adminClient.deleteArtifact(groupId, adminId));
        // Check that API returns 204 No Content when deleting artifact by developer
        Assertions.assertTrue(developerClient.deleteArtifact(groupId, developerId));
        // Check that API returns 204 No Content when deleting artifact by readonly
        Assertions.assertTrue(readonlyClient.deleteArtifact(groupId, readonlyId));
        // --- delete action on another artifact
        // Check that API returns 204 No Content when deleting developer artifact by admin
        Assertions.assertTrue(adminClient.deleteArtifact(groupId, developerId + secondId));
        // Check that API returns 204 No Content when deleting readonly artifact by admin
        Assertions.assertTrue(adminClient.deleteArtifact(groupId, readonlyId + secondId));
        // Check that API returns 204 No Content when deleting admin artifact by developer
        Assertions.assertTrue(developerClient.deleteArtifact(groupId, adminId + secondId));
        // Check that API returns 204 No Content when deleting readonly artifact by developer
        Assertions.assertTrue(developerClient.deleteArtifact(groupId, readonlyId + thirdId));
        // Check that API returns 204 No Content when deleting admin artifact by readonly
        Assertions.assertTrue(readonlyClient.deleteArtifact(groupId, adminId + thirdId));
        // Check that API returns 204 No Content when deleting developer artifact by readonly
        Assertions.assertTrue(readonlyClient.deleteArtifact(groupId, developerId + thirdId));

        // ENABLE ROLE BASED AUTHORIZATION BY TOKEN IN REGISTRY AND TEST IT
        // TODO: Add artifact rules
        // Set environment variable ROLE_BASED_AUTHZ_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("ROLE_BASED_AUTHZ_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // --- global validity rule by admin
        // Check that API returns 204 No Content when enabling global validity rule by admin
        Assertions.assertTrue(adminClient.enableGlobalValidityRule());
        // Check that API returns 200 OK when listing global rules by admin
        Assertions.assertNotNull(adminClient.listGlobalRules());
        // Check that API returns 200 OK when updating global validity rule by admin
        Assertions.assertTrue(adminClient.updateGlobalValidityRule(ValidityLevel.SYNTAX_ONLY));
        // Check that API returns 200 OK when getting global validity rule by admin
        Assertions.assertNotNull(adminClient.getGlobalValidityRule());
        // Check that API returns 204 No Content when disabling global validity rule by admin
        Assertions.assertTrue(adminClient.disableGlobalValidityRule());
        // --- global validity rule by developer
        // Check that API returns 403 Forbidden when enabling global validity rule by developer
        Assertions.assertTrue(developerClient.enableGlobalValidityRule(HttpStatus.SC_FORBIDDEN));
        // SETUP: Enable global validity rule for test of developer permission
        Assertions.assertTrue(adminClient.enableGlobalValidityRule());
        // Check that API returns 403 Forbidden when listing global rules by developer
        Assertions.assertTrue(developerClient.listGlobalRules(HttpStatus.SC_FORBIDDEN).isEmpty());
        // Check that API returns 403 Forbidden when updating global validity rule by developer
        Assertions.assertTrue(
                developerClient.updateGlobalValidityRule(ValidityLevel.SYNTAX_ONLY, HttpStatus.SC_FORBIDDEN)
        );
        // Check that API returns 403 Forbidden when getting global validity rule by developer
        Assertions.assertNull(developerClient.getGlobalValidityRule(HttpStatus.SC_FORBIDDEN));
        // Check that API returns 403 Forbidden when disabling global validity rule by developer
        Assertions.assertTrue(developerClient.disableGlobalValidityRule(HttpStatus.SC_FORBIDDEN));
        // TEARDOWN: Disable global validity rule after test of developer permission
        Assertions.assertTrue(adminClient.disableGlobalValidityRule());
        // --- global validity rule by readonly
        // Check that API returns 403 Forbidden when enabling global validity rule by readonly
        Assertions.assertTrue(readonlyClient.enableGlobalValidityRule(HttpStatus.SC_FORBIDDEN));
        // SETUP: Enable global validity rule for test of readonly permission
        Assertions.assertTrue(adminClient.enableGlobalValidityRule());
        // Check that API returns 403 Forbidden when listing global rules by readonly
        Assertions.assertTrue(readonlyClient.listGlobalRules(HttpStatus.SC_FORBIDDEN).isEmpty());
        // Check that API returns 403 Forbidden when updating global validity rule by readonly
        Assertions.assertTrue(
                readonlyClient.updateGlobalValidityRule(ValidityLevel.SYNTAX_ONLY, HttpStatus.SC_FORBIDDEN)
        );
        // Check that API returns 403 Forbidden when getting global validity rule by readonly
        Assertions.assertNull(readonlyClient.getGlobalValidityRule(HttpStatus.SC_FORBIDDEN));
        // Check that API returns 403 Forbidden when disabling global validity rule by readonly
        Assertions.assertTrue(readonlyClient.disableGlobalValidityRule(HttpStatus.SC_FORBIDDEN));
        // TEARDOWN: Disable global validity rule after test of readonly permission
        Assertions.assertTrue(adminClient.disableGlobalValidityRule());
        // --- global compatibility rule by admin
        // Check that API returns 204 No Content when enabling global compatibility rule by admin
        Assertions.assertTrue(adminClient.enableGlobalCompatibilityRule());
        // Check that API returns 200 OK when listing global rules by admin
        Assertions.assertNotNull(adminClient.listGlobalRules());
        // Check that API returns 200 OK when updating global compatibility rule by admin
        Assertions.assertTrue(adminClient.updateGlobalCompatibilityRule(CompatibilityLevel.BACKWARD_TRANSITIVE));
        // Check that API returns 200 OK when getting global compatibility rule by admin
        Assertions.assertNotNull(adminClient.getGlobalCompatibilityRule());
        // Check that API returns 204 No Content when disabling global compatibility rule by admin
        Assertions.assertTrue(adminClient.disableGlobalCompatibilityRule());
        // --- global compatibility rule by developer
        // Check that API returns 403 Forbidden when enabling global compatibility rule by developer
        Assertions.assertTrue(developerClient.enableGlobalCompatibilityRule(HttpStatus.SC_FORBIDDEN));
        // SETUP: Enable global compatibility rule for test of developer permission
        Assertions.assertTrue(adminClient.enableGlobalCompatibilityRule());
        // Check that API returns 403 Forbidden when listing global rules by developer
        Assertions.assertTrue(developerClient.listGlobalRules(HttpStatus.SC_FORBIDDEN).isEmpty());
        // Check that API returns 403 Forbidden when updating global compatibility rule by developer
        Assertions.assertTrue(
                developerClient.updateGlobalCompatibilityRule(
                        CompatibilityLevel.BACKWARD_TRANSITIVE,
                        HttpStatus.SC_FORBIDDEN
                )
        );
        // Check that API returns 403 Forbidden when getting global compatibility rule by developer
        Assertions.assertNull(developerClient.getGlobalCompatibilityRule(HttpStatus.SC_FORBIDDEN));
        // Check that API returns 403 Forbidden when disabling global compatibility rule by developer
        Assertions.assertTrue(developerClient.disableGlobalCompatibilityRule(HttpStatus.SC_FORBIDDEN));
        // TEARDOWN: Disable global compatibility rule after test of developer permission
        Assertions.assertTrue(adminClient.disableGlobalCompatibilityRule());
        // --- global compatibility rule by readonly
        // Check that API returns 403 Forbidden when enabling global compatibility rule by readonly
        Assertions.assertTrue(readonlyClient.enableGlobalCompatibilityRule(HttpStatus.SC_FORBIDDEN));
        // SETUP: Enable global compatibility rule for test of readonly permission
        Assertions.assertTrue(adminClient.enableGlobalCompatibilityRule());
        // Check that API returns 403 Forbidden when listing global rules by readonly
        Assertions.assertTrue(readonlyClient.listGlobalRules(HttpStatus.SC_FORBIDDEN).isEmpty());
        // Check that API returns 403 Forbidden when updating global compatibility rule by readonly
        Assertions.assertTrue(
                readonlyClient.updateGlobalCompatibilityRule(
                        CompatibilityLevel.BACKWARD_TRANSITIVE,
                        HttpStatus.SC_FORBIDDEN
                )
        );
        // Check that API returns 403 Forbidden when getting global compatibility rule by readonly
        Assertions.assertNull(readonlyClient.getGlobalCompatibilityRule(HttpStatus.SC_FORBIDDEN));
        // Check that API returns 403 Forbidden when disabling global compatibility rule by readonly
        Assertions.assertTrue(readonlyClient.disableGlobalCompatibilityRule(HttpStatus.SC_FORBIDDEN));
        // TEARDOWN: Disable global compatibility rule after test of readonly permission
        Assertions.assertTrue(adminClient.disableGlobalCompatibilityRule());
        // --- list action
        // Check that API returns 200 OK when listing artifacts by admin
        Assertions.assertNotNull(adminClient.listArtifacts());
        // Check that API returns 200 OK when listing artifacts by developer
        Assertions.assertNotNull(developerClient.listArtifacts());
        // Check that API returns 200 OK when listing artifacts by readonly
        Assertions.assertNotNull(readonlyClient.listArtifacts());
        // --- create action
        // Check that API returns 200 OK when creating artifact by admin
        Assertions.assertTrue(adminClient.createArtifact(groupId, adminId, type, initialContent));
        // Check that API returns 200 OK when creating artifact by developer
        Assertions.assertTrue(developerClient.createArtifact(groupId, developerId, type, initialContent));
        // Check that API returns 200 OK when creating second artifact by admin
        Assertions.assertTrue(adminClient.createArtifact(groupId, adminId + secondId, type, initialContent));
        // Check that API returns 200 OK when creating second artifact by developer
        Assertions.assertTrue(developerClient.createArtifact(groupId, developerId + secondId, type, initialContent));
        // Check that API returns 403 Forbidden when creating artifact by readonly
        Assertions.assertTrue(
                readonlyClient.createArtifact(groupId, readonlyId, type, initialContent, HttpStatus.SC_FORBIDDEN)
        );
        // --- list action on group
        // Check that API returns 200 OK when listing group artifacts by admin
        Assertions.assertNotNull(adminClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when listing group artifacts by developer
        Assertions.assertNotNull(developerClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when listing group artifacts by readonly
        Assertions.assertNotNull(readonlyClient.listGroupArtifacts(groupId));
        // --- read action on own artifact
        // Check that API returns 200 OK when reading artifact by admin
        Assertions.assertNotNull(adminClient.readArtifactContent(groupId, adminId));
        // Check that API returns 200 OK when reading artifact by developer
        Assertions.assertNotNull(developerClient.readArtifactContent(groupId, developerId));
        // NOTE: Readonly user does not have any artifact, no action to do here
        // --- read action on another artifact
        // Check that API returns 200 OK when reading developer artifact by admin
        Assertions.assertNotNull(adminClient.readArtifactContent(groupId, developerId));
        // Check that API returns 200 OK when reading admin artifact by developer
        Assertions.assertNotNull(developerClient.readArtifactContent(groupId, adminId));
        // Check that API returns 200 OK when reading admin artifact by readonly
        Assertions.assertNotNull(readonlyClient.readArtifactContent(groupId, adminId));
        // Check that API returns 200 OK when reading developer artifact by readonly
        Assertions.assertNotNull(readonlyClient.readArtifactContent(groupId, developerId));
        // --- update action on own artifact
        // Check that API returns 200 OK when updating admin artifact by admin
        Assertions.assertTrue(adminClient.updateArtifact(groupId, adminId, updatedContent));
        // Check that API returns 200 OK when updating developer artifact by developer
        Assertions.assertTrue(developerClient.updateArtifact(groupId, developerId, updatedContent));
        // NOTE: Readonly user does not have any artifact, no action to do here
        // --- update action on another artifact
        // Check that API returns 200 OK when updating developer artifact by admin
        Assertions.assertTrue(adminClient.updateArtifact(groupId, developerId, secondUpdatedContent));
        // Check that API returns 200 OK when updating admin artifact by developer
        Assertions.assertTrue(developerClient.updateArtifact(groupId, adminId, secondUpdatedContent));
        // Check that API returns 403 Forbidden when updating admin artifact by readonly
        Assertions.assertTrue(
                readonlyClient.updateArtifact(groupId, adminId, initialContent, HttpStatus.SC_FORBIDDEN)
        );
        // Check that API returns 403 Forbidden when updating developer artifact by readonly
        Assertions.assertTrue(
                readonlyClient.updateArtifact(groupId, developerId, initialContent, HttpStatus.SC_FORBIDDEN)
        );
        // --- delete action on own artifact
        // Check that API returns 204 No Content when deleting artifact by admin
        Assertions.assertTrue(adminClient.deleteArtifact(groupId, adminId));
        // Check that API returns 204 No Content when deleting artifact by developer
        Assertions.assertTrue(developerClient.deleteArtifact(groupId, developerId));
        // NOTE: Readonly user does not have any artifact, no action to do here
        // --- delete action on another artifact
        // Check that API returns 403 Forbidden when deleting second admin artifact by readonly
        Assertions.assertTrue(readonlyClient.deleteArtifact(groupId, adminId + secondId, HttpStatus.SC_FORBIDDEN));
        // Check that API returns 403 Forbidden when deleting second developer artifact by readonly
        Assertions.assertTrue(
                readonlyClient.deleteArtifact(groupId, developerId + secondId, HttpStatus.SC_FORBIDDEN)
        );
        // Check that API returns 204 No Content when deleting second developer artifact by admin
        Assertions.assertTrue(adminClient.deleteArtifact(groupId, developerId + secondId));
        // Check that API returns 204 No Content when deleting second admin artifact by developer
        Assertions.assertTrue(developerClient.deleteArtifact(groupId, adminId + secondId));

        // DISABLE ROLE BASED AUTHORIZATION BY TOKEN IN REGISTRY AND TEST IT
        // Set environment variable ROLE_BASED_AUTHZ_ENABLED of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("ROLE_BASED_AUTHZ_ENABLED");
            setValue("false");
        }});
        // Wait for API availability
        Assertions.assertTrue(adminClient.waitServiceAvailable());
        // TODO: Add artifact rules
        // --- global validity rule by admin
        // Check that API returns 204 No Content when enabling global validity rule by admin
        Assertions.assertTrue(adminClient.enableGlobalValidityRule());
        // Check that API returns 200 OK when listing global rules by admin
        Assertions.assertNotNull(adminClient.listGlobalRules());
        // Check that API returns 200 OK when updating global validity rule by admin
        Assertions.assertTrue(adminClient.updateGlobalValidityRule(ValidityLevel.SYNTAX_ONLY));
        // Check that API returns 200 OK when getting global validity rule by admin
        Assertions.assertNotNull(adminClient.getGlobalValidityRule());
        // Check that API returns 204 No Content when disabling global validity rule by admin
        Assertions.assertTrue(adminClient.disableGlobalValidityRule());
        // --- global validity rule by developer
        // Check that API returns 204 No Content when enabling global validity rule by developer
        Assertions.assertTrue(developerClient.enableGlobalValidityRule());
        // Check that API returns 200 OK when listing global rules by developer
        Assertions.assertNotNull(developerClient.listGlobalRules());
        // Check that API returns 200 OK when updating global validity rule by developer
        Assertions.assertTrue(developerClient.updateGlobalValidityRule(ValidityLevel.SYNTAX_ONLY));
        // Check that API returns 200 OK when getting global validity rule by developer
        Assertions.assertNotNull(developerClient.getGlobalValidityRule());
        // Check that API returns 204 No Content when disabling global validity rule by developer
        Assertions.assertTrue(developerClient.disableGlobalValidityRule());
        // --- global validity rule by readonly
        // Check that API returns 204 No Content when enabling global validity rule by developer
        Assertions.assertTrue(readonlyClient.enableGlobalValidityRule());
        // Check that API returns 200 OK when listing global rules by developer
        Assertions.assertNotNull(readonlyClient.listGlobalRules());
        // Check that API returns 200 OK when updating global validity rule by developer
        Assertions.assertTrue(readonlyClient.updateGlobalValidityRule(ValidityLevel.SYNTAX_ONLY));
        // Check that API returns 200 OK when getting global validity rule by developer
        Assertions.assertNotNull(readonlyClient.getGlobalValidityRule());
        // Check that API returns 204 No Content when disabling global validity rule by developer
        Assertions.assertTrue(readonlyClient.disableGlobalValidityRule());
        // --- global compatibility rule by admin
        // Check that API returns 204 No Content when enabling global compatibility rule by admin
        Assertions.assertTrue(adminClient.enableGlobalCompatibilityRule());
        // Check that API returns 200 OK when listing global rules by admin
        Assertions.assertNotNull(adminClient.listGlobalRules());
        // Check that API returns 200 OK when updating global compatibility rule by admin
        Assertions.assertTrue(adminClient.updateGlobalCompatibilityRule(CompatibilityLevel.BACKWARD_TRANSITIVE));
        // Check that API returns 200 OK when getting global compatibility rule by admin
        Assertions.assertNotNull(adminClient.getGlobalCompatibilityRule());
        // Check that API returns 204 No Content when disabling global compatibility rule by admin
        Assertions.assertTrue(adminClient.disableGlobalCompatibilityRule());
        // --- global compatibility rule by developer
        // Check that API returns 204 No Content when enabling global compatibility rule by developer
        Assertions.assertTrue(developerClient.enableGlobalCompatibilityRule());
        // Check that API returns 200 OK when listing global rules by developer
        Assertions.assertNotNull(developerClient.listGlobalRules());
        // Check that API returns 200 OK when updating global compatibility rule by developer
        Assertions.assertTrue(developerClient.updateGlobalCompatibilityRule(CompatibilityLevel.BACKWARD_TRANSITIVE));
        // Check that API returns 200 OK when getting global compatibility rule by developer
        Assertions.assertNotNull(developerClient.getGlobalCompatibilityRule());
        // Check that API returns 204 No Content when disabling global compatibility rule by developer
        Assertions.assertTrue(developerClient.disableGlobalCompatibilityRule());
        // --- global compatibility rule by readonly
        // Check that API returns 204 No Content when enabling global compatibility rule by developer
        Assertions.assertTrue(readonlyClient.enableGlobalCompatibilityRule());
        // Check that API returns 200 OK when listing global rules by developer
        Assertions.assertNotNull(readonlyClient.listGlobalRules());
        // Check that API returns 200 OK when updating global compatibility rule by developer
        Assertions.assertTrue(readonlyClient.updateGlobalCompatibilityRule(CompatibilityLevel.BACKWARD_TRANSITIVE));
        // Check that API returns 200 OK when getting global compatibility rule by developer
        Assertions.assertNotNull(readonlyClient.getGlobalCompatibilityRule());
        // Check that API returns 204 No Content when disabling global compatibility rule by developer
        Assertions.assertTrue(readonlyClient.disableGlobalCompatibilityRule());
        // --- list action
        // Check that API returns 200 OK when listing artifacts by admin
        Assertions.assertNotNull(adminClient.listArtifacts());
        // Check that API returns 200 OK when listing artifacts by developer
        Assertions.assertNotNull(developerClient.listArtifacts());
        // Check that API returns 200 OK when listing artifacts by readonly
        Assertions.assertNotNull(readonlyClient.listArtifacts());
        // --- create action
        // Check that API returns 200 OK when creating artifact by admin
        Assertions.assertTrue(adminClient.createArtifact(groupId, adminId, type, initialContent));
        // Check that API returns 200 OK when creating artifact by developer
        Assertions.assertTrue(developerClient.createArtifact(groupId, developerId, type, initialContent));
        // Check that API returns 200 OK when creating artifact by readonly
        Assertions.assertTrue(readonlyClient.createArtifact(groupId, readonlyId, type, initialContent));
        // Check that API returns 200 OK when creating second artifact by admin
        Assertions.assertTrue(adminClient.createArtifact(groupId, adminId + secondId, type, initialContent));
        // Check that API returns 200 OK when creating second artifact by developer
        Assertions.assertTrue(developerClient.createArtifact(groupId, developerId + secondId, type, initialContent));
        // Check that API returns 200 OK when creating artifact by readonly
        Assertions.assertTrue(readonlyClient.createArtifact(groupId, readonlyId + secondId, type, initialContent));
        // Check that API returns 200 OK when creating third artifact by admin
        Assertions.assertTrue(adminClient.createArtifact(groupId, adminId + thirdId, type, initialContent));
        // Check that API returns 200 OK when creating third artifact by developer
        Assertions.assertTrue(developerClient.createArtifact(groupId, developerId + thirdId, type, initialContent));
        // Check that API returns 200 OK when creating third artifact by readonly
        Assertions.assertTrue(readonlyClient.createArtifact(groupId, readonlyId + thirdId, type, initialContent));
        // --- list action on group
        // Check that API returns 200 OK when listing group artifacts by admin
        Assertions.assertNotNull(adminClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when listing group artifacts by developer
        Assertions.assertNotNull(developerClient.listGroupArtifacts(groupId));
        // Check that API returns 200 OK when listing group artifacts by readonly
        Assertions.assertNotNull(readonlyClient.listGroupArtifacts(groupId));
        // --- read action on own artifact
        // Check that API returns 200 OK when reading artifact by admin
        Assertions.assertNotNull(adminClient.readArtifactContent(groupId, adminId));
        // Check that API returns 200 OK when reading artifact by developer
        Assertions.assertNotNull(developerClient.readArtifactContent(groupId, developerId));
        // Check that API returns 200 OK when reading artifact by readonly
        Assertions.assertNotNull(readonlyClient.readArtifactContent(groupId, readonlyId));
        // --- read action on another artifact
        // Check that API returns 200 OK when reading developer artifact by admin
        Assertions.assertNotNull(adminClient.readArtifactContent(groupId, developerId));
        // Check that API returns 200 OK when reading readonly artifact by admin
        Assertions.assertNotNull(adminClient.readArtifactContent(groupId, readonlyId));
        // Check that API returns 200 OK when reading admin artifact by developer
        Assertions.assertNotNull(developerClient.readArtifactContent(groupId, adminId));
        // Check that API returns 200 OK when reading readonly artifact by developer
        Assertions.assertNotNull(developerClient.readArtifactContent(groupId, readonlyId));
        // Check that API returns 200 OK when reading admin artifact by readonly
        Assertions.assertNotNull(readonlyClient.readArtifactContent(groupId, adminId));
        // Check that API returns 200 OK when reading developer artifact by readonly
        Assertions.assertNotNull(readonlyClient.readArtifactContent(groupId, developerId));
        // --- update action on own artifact
        // Check that API returns 200 OK when updating admin artifact by admin
        Assertions.assertTrue(adminClient.updateArtifact(groupId, adminId, updatedContent));
        // Check that API returns 200 OK when updating developer artifact by developer
        Assertions.assertTrue(developerClient.updateArtifact(groupId, developerId, updatedContent));
        // Check that API returns 200 OK when updating readonly artifact by readonly
        Assertions.assertTrue(readonlyClient.updateArtifact(groupId, readonlyId, updatedContent));
        // --- update action on another artifact
        // Check that API returns 200 OK when updating developer artifact by admin
        Assertions.assertTrue(adminClient.updateArtifact(groupId, developerId, secondUpdatedContent));
        // Check that API returns 200 OK when updating readonly artifact by admin
        Assertions.assertTrue(adminClient.updateArtifact(groupId, readonlyId, secondUpdatedContent));
        // Check that API returns 200 OK when updating admin artifact by developer
        Assertions.assertTrue(developerClient.updateArtifact(groupId, adminId, secondUpdatedContent));
        // Check that API returns 200 OK when updating readonly artifact by developer
        Assertions.assertTrue(developerClient.updateArtifact(groupId, readonlyId, thirdUpdatedContent));
        // Check that API returns 200 OK when updating admin artifact by readonly
        Assertions.assertTrue(readonlyClient.updateArtifact(groupId, adminId, thirdUpdatedContent));
        // Check that API returns 200 OK when updating developer artifact by readonly
        Assertions.assertTrue(readonlyClient.updateArtifact(groupId, developerId, thirdUpdatedContent));
        // --- delete action on own artifact
        // Check that API returns 204 No Content when deleting artifact by admin
        Assertions.assertTrue(adminClient.deleteArtifact(groupId, adminId));
        // Check that API returns 204 No Content when deleting artifact by developer
        Assertions.assertTrue(developerClient.deleteArtifact(groupId, developerId));
        // Check that API returns 204 No Content when deleting artifact by readonly
        Assertions.assertTrue(readonlyClient.deleteArtifact(groupId, readonlyId));
        // --- delete action on another artifact
        // Check that API returns 204 No Content when deleting developer artifact by admin
        Assertions.assertTrue(adminClient.deleteArtifact(groupId, developerId + secondId));
        // Check that API returns 204 No Content when deleting readonly artifact by admin
        Assertions.assertTrue(adminClient.deleteArtifact(groupId, readonlyId + secondId));
        // Check that API returns 204 No Content when deleting admin artifact by developer
        Assertions.assertTrue(developerClient.deleteArtifact(groupId, adminId + secondId));
        // Check that API returns 204 No Content when deleting readonly artifact by developer
        Assertions.assertTrue(developerClient.deleteArtifact(groupId, readonlyId + thirdId));
        // Check that API returns 204 No Content when deleting admin artifact by readonly
        Assertions.assertTrue(readonlyClient.deleteArtifact(groupId, adminId + thirdId));
        // Check that API returns 204 No Content when deleting developer artifact by readonly
        Assertions.assertTrue(readonlyClient.deleteArtifact(groupId, developerId + thirdId));
    }
}
