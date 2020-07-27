package io.apicurio.registry;

import com.tngtech.keycloakmock.api.KeycloakMock;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Collections;
import java.util.Map;

public class KeycloakTestResource implements QuarkusTestResourceLifecycleManager {

    private KeycloakMock keycloakMock;

    @Override
    public Map<String, String> start() {

        keycloakMock = new KeycloakMock(8090, "registry");

        keycloakMock.start();

        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        keycloakMock.stop();
    }
}
