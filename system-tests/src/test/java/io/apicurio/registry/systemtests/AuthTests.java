package io.apicurio.registry.systemtests;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.client.ApicurioRegistryApiClient;
import io.apicurio.registry.systemtests.client.ArtifactType;
import io.apicurio.registry.systemtests.framework.ApicurioRegistryUtils;
import io.apicurio.registry.systemtests.framework.DeploymentUtils;
import io.apicurio.registry.systemtests.framework.KeycloakUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;

import java.util.List;

public class AuthTests {
    protected static Logger LOGGER = LoggerUtils.getLogger();

    public static void testAnonymousReadAccess(ApicurioRegistry apicurioRegistry) {
        testAnonymousReadAccess(apicurioRegistry, null, null, false);
    }

    public static void testAnonymousReadAccess(
            ApicurioRegistry apicurioRegistry,
            String username,
            String password,
            boolean useToken
    ) {
        /* RUN PRE-TEST ACTIONS */

        // INITIALIZE API CLIENT
        // Wait for readiness of registry hostname
        Assertions.assertTrue(ApicurioRegistryUtils.waitApicurioRegistryHostnameReady(apicurioRegistry));
        // Create API client
        ApicurioRegistryApiClient client = new ApicurioRegistryApiClient(
                // Set hostname only
                ApicurioRegistryUtils.getApicurioRegistryHostname(apicurioRegistry)
        );
        // If access token is needed
        if (useToken) {
            // Get access token from Keycloak and update API client with it
            client.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
        }
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());

        // PREPARE NECESSARY VARIABLES
        // Get registry deployment
        Deployment deployment = Kubernetes.getDeployment(
                apicurioRegistry.getMetadata().getNamespace(),
                apicurioRegistry.getMetadata().getName() + "-deployment"
        );
        // Save state of deployment's environment variables
        List<EnvVar> savedEnvVars = DeploymentUtils.cloneDeploymentEnvVars(deployment);
        // Define artifact group ID
        String groupId = "default";
        // Define artifact ID
        String id = "anonymous-read-access-test";
        // Define artifact ID for create actions that should fail
        String failId = id + "-fail";
        // Define artifact type
        ArtifactType type = ArtifactType.JSON;
        // Define artifact content
        String content = "{}";

        // PREPARE REGISTRY CONTENT
        // Create artifact for test
        Assertions.assertTrue(client.createArtifact(groupId, id, type, content));

        // PREPARE API CLIENT
        // If access token is used
        if (useToken) {
            // Temporarily remove access token from client to try unauthenticated/anonymous access
            client.setToken(null);
        }

        /* RUN TEST ACTIONS */

        // ENABLE REGISTRY AUTHENTICATION WITHOUT ANONYMOUS READ ACCESS AND TEST IT
        // Set environment variable AUTH_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("AUTH_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertTrue(client.checkUnauthorized());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(groupId, failId, type, content, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

        // ENABLE ANONYMOUS READ ACCESS IN REGISTRY AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to true
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED");
            setValue("true");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 200 OK when reading artifacts
        Assertions.assertNotNull(client.listArtifacts());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(groupId, failId, type, content, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

        // DISABLE ANONYMOUS READ ACCESS IN REGISTRY AUTHENTICATION AND TEST IT
        // Set environment variable REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED of deployment to false
        DeploymentUtils.createOrReplaceDeploymentEnvVar(deployment, new EnvVar() {{
            setName("REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED");
            setValue("false");
        }});
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // Check that API returns 401 Unauthorized when reading artifacts
        Assertions.assertTrue(client.checkUnauthorized());
        // Check that API returns 401 Unauthorized when creating artifact
        Assertions.assertTrue(client.createArtifact(groupId, failId, type, content, HttpStatus.SC_UNAUTHORIZED));
        // Check that API returns 401 Unauthorized when deleting artifact
        Assertions.assertTrue(client.deleteArtifact(groupId, id, HttpStatus.SC_UNAUTHORIZED));

        /* RUN POST-TEST ACTIONS */

        // RETURN REGISTRY INTO PRE-TEST STATE
        // Restore pre-test state of environment variables
        DeploymentUtils.setDeploymentEnvVars(deployment, savedEnvVars);
        // Wait for API availability
        Assertions.assertTrue(client.waitServiceAvailable());
        // If access token was needed
        if (useToken) {
            // Get access token from Keycloak again and update API client with it
            client.setToken(KeycloakUtils.getAccessToken(apicurioRegistry, username, password));
        }
        // Remove artifact created for test
        Assertions.assertTrue(client.deleteArtifact(groupId, id));
    }
}
