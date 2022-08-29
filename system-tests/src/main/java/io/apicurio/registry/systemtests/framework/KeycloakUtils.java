package io.apicurio.registry.systemtests.framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtests.executor.Exec;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.RouteResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.ServiceResourceType;
import io.fabric8.kubernetes.api.model.Secret;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class KeycloakUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String getKeycloakFilePath(String filename) {
        return Paths.get(Environment.TESTSUITE_PATH, "kubefiles", "keycloak", filename).toString();
    }

    public static void deployKeycloak(ExtensionContext testContext) {
        deployKeycloak(testContext, Constants.TESTSUITE_NAMESPACE);
    }

    public static void deployKeycloak(ExtensionContext testContext, String namespace) {
        LOGGER.info("Deploying Keycloak...");

        ResourceManager manager = ResourceManager.getInstance();

        // Deploy Keycloak server
        Exec.executeAndCheck(
                "oc",
                "apply",
                "-n", namespace,
                "-f", getKeycloakFilePath("keycloak.yaml")
        );

        // Wait for Keycloak server to be ready
        Assertions.assertTrue(ResourceUtils.waitStatefulSetReady(namespace, "keycloak"));

        // Create Keycloak HTTP Service and wait for its readiness
        manager.createResource(testContext, true, ServiceResourceType.getDefaultKeycloakHttp(namespace));

        // Create Keycloak Route and wait for its readiness
        manager.createResource(testContext, true, RouteResourceType.getDefaultKeycloak(namespace));

        // Log Keycloak URL
        LOGGER.info("Keycloak URL: {}", getDefaultKeycloakURL(namespace));

        // Create Keycloak Realm
        Exec.executeAndCheck(
                "oc",
                "apply",
                "-n", namespace,
                "-f", getKeycloakFilePath("keycloak-realm.yaml")
        );

        // TODO: Wait for Keycloak Realm readiness, but API model not available

        LOGGER.info("Keycloak should be deployed.");
    }

    public static void removeKeycloak() {
        removeKeycloak(Constants.TESTSUITE_NAMESPACE);
    }

    public static void removeKeycloak(String namespace) {
        LOGGER.info("Removing Keycloak...");

        Exec.executeAndCheck(
                "oc",
                "delete",
                "-n", namespace,
                "-f", getKeycloakFilePath("keycloak-realm.yaml")
        );

        Exec.executeAndCheck(
                "oc",
                "delete",
                "-n", namespace,
                "-f", getKeycloakFilePath("keycloak.yaml")
        );

        LOGGER.info("Keycloak should be removed.");
    }

    public static String getKeycloakURL(String namespace, String name, boolean secured) {
        String scheme = secured ? "https://" : "http://";

        return scheme + Kubernetes.getRouteHost(namespace, name) + "/auth";
    }

    public static String getDefaultKeycloakURL() {
        return getDefaultKeycloakURL(Constants.TESTSUITE_NAMESPACE);
    }

    public static String getDefaultKeycloakURL(String namespace) {
        return getKeycloakURL(namespace, Constants.SSO_HTTP_SERVICE, false);
    }

    public static String getDefaultKeycloakAdminURL() {
        return getDefaultKeycloakAdminURL(Constants.TESTSUITE_NAMESPACE);
    }

    public static String getDefaultKeycloakAdminURL(String namespace) {
        return getKeycloakURL(namespace, "keycloak", true);
    }

    private static HttpRequest.BodyPublisher ofFormData(Map<Object, Object> data) {
        StringBuilder stringBuilder = new StringBuilder();

        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append("&");
            }

            stringBuilder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            stringBuilder.append("=");
            stringBuilder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }

        return HttpRequest.BodyPublishers.ofString(stringBuilder.toString());
    }

    public static String getAccessToken(ApicurioRegistry apicurioRegistry, String username, String password) {
        // Get Keycloak URL of Apicurio Registry
        String keycloakUrl = apicurioRegistry.getSpec().getConfiguration().getSecurity().getKeycloak().getUrl();
        // Get Keycloak Realm of Apicurio Registry
        String keycloakRealm = apicurioRegistry.getSpec().getConfiguration().getSecurity().getKeycloak().getRealm();
        // Construct token API URI of Keycloak Realm
        URI keycloakRealmUrl = HttpClientUtils.buildURI(
                "%s/realms/%s/protocol/openid-connect/token", keycloakUrl, keycloakRealm
        );
        // Get Keycloak API client ID of Apicurio Registry
        String clientId = apicurioRegistry.getSpec().getConfiguration().getSecurity().getKeycloak().getApiClientId();

        // Prepare request data
        Map<Object, Object> data = new HashMap<>();
        data.put("grant_type", "password");
        data.put("client_id", clientId);
        data.put("username", username);
        data.put("password", password);

        LOGGER.info("Requesting access token from {}...", keycloakRealmUrl);

        // Create request
        HttpRequest request = HttpClientUtils.newBuilder()
                .uri(keycloakRealmUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofFormData(data))
                .build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return null;
        }

        // Return access token
        try {
            return MAPPER.readValue(response.body(), Map.class).get("access_token").toString();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getAdminAccessToken() {
        // Get secret with Keycloak admin credentials
        Secret secret = Kubernetes.getSecret(Constants.TESTSUITE_NAMESPACE, "credential-" + Constants.SSO_NAME);
        // Get Keycloak admin username
        String username = Base64Utils.decode(secret.getData().get("ADMIN_USERNAME"));
        // Get Keycloak admin password
        String password = Base64Utils.decode(secret.getData().get("ADMIN_PASSWORD"));
        // Get Keycloak admin URL
        String url = getKeycloakURL(Constants.TESTSUITE_NAMESPACE, "keycloak", true);
        // Construct token API URI of admin Keycloak Realm
        URI tokenUrl = HttpClientUtils.buildURI(
                "%s/realms/%s/protocol/openid-connect/token", url, Constants.SSO_REALM_ADMIN
        );

        // Prepare request data
        Map<Object, Object> data = new HashMap<>();
        data.put("grant_type", "password");
        data.put("client_id", Constants.SSO_ADMIN_CLIENT_ID);
        data.put("username", username);
        data.put("password", password);

        // Log info about current action
        LOGGER.info("Requesting admin access token from {}...", tokenUrl);

        // Create request
        HttpRequest request = HttpClientUtils.newBuilder()
                .uri(tokenUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofFormData(data))
                .build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return null;
        }

        // Return access token
        try {
            return MAPPER.readValue(response.body(), Map.class).get("access_token").toString();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}