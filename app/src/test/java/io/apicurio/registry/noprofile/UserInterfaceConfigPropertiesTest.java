package io.apicurio.registry.noprofile;

import io.apicurio.registry.ui.UserInterfaceConfigProperties;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UserInterfaceConfigPropertiesTest {

    @Inject
    UserInterfaceConfigProperties config;

    @Test
    void logoutUrlIsEmptyWhenUnset() {
        assertTrue(config.authOidcLogoutUrl.isEmpty());
    }
}
