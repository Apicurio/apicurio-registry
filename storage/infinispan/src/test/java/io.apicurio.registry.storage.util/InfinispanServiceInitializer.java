package io.apicurio.registry.storage.util;

import io.apicurio.registry.auth.KeycloakResourceManager;
import io.apicurio.registry.util.ClusterInitializer;
import io.apicurio.registry.util.ServiceInitializer;
import io.quarkus.test.common.QuarkusTestResource;

import java.util.Map;

/**
 * @author Carles Arnal
 */
@QuarkusTestResource(KeycloakResourceManager.class)
public class InfinispanServiceInitializer implements ServiceInitializer, ClusterInitializer {

    private KeycloakResourceManager keycloakResourceManager;

    @Override
    public Map<String, String> startCluster() {
        keycloakResourceManager = new KeycloakResourceManager();
        return keycloakResourceManager.start();
    }

    @Override
    public void stopCluster() {
        if (keycloakResourceManager != null) {
            keycloakResourceManager.stop();
        }
    }
}
