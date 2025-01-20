package io.apicurio.registry.utils.tests;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.HashMap;
import java.util.Map;

public class KeycloakTestContainerManager implements QuarkusTestResourceLifecycleManager {

    static final Logger LOGGER = LoggerFactory.getLogger(KeycloakTestContainerManager.class);
    private KeycloakContainer server;
    public static String ADMIN_CLIENT_ID = "admin-client";
    public static String DEVELOPER_CLIENT_ID = "developer-client";
    public static String DEVELOPER_2_CLIENT_ID = "developer-2-client";
    public static String READONLY_CLIENT_ID = "readonly-client";

    public static String NO_ROLE_CLIENT_ID = "no-role-client";
    public static String WRONG_CREDS_CLIENT_ID = "wrong-client";

    @Override
    public Map<String, String> start() {

        server = new KeycloakContainer().withNetwork(Network.SHARED).withRealmImportFile("/realm.json");
        server.start();

        server.waitingFor(Wait.forLogMessage(
                ".*[org.keycloak.quarkus.runtime.KeycloakMain] (main) Running the server in development mode.*",
                1));

        Map<String, String> props = new HashMap<>();

        String authUrl = server.getAuthServerUrl() + "/realms/registry";
        String tokenUrl = authUrl + "/protocol/openid-connect/token/";

        props.put("quarkus.oidc.auth-server-url", authUrl);
        props.put("quarkus.oidc.token-path", tokenUrl);
        props.put("quarkus.oidc.tenant-enabled", "true");
        props.put("apicurio.auth.role-based-authorization", "true");
        props.put("apicurio.auth.owner-only-authorization", "true");
        props.put("apicurio.auth.admin-override.enabled", "true");
        props.put("apicurio.authn.basic-client-credentials.enabled", "true");

        return props;
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.stop();
            LOGGER.info("Keycloak was shut down");
            server = null;
        }
    }
}
