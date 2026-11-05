package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.rest.v3.beans.UserInterfaceConfig;
import io.apicurio.registry.rest.v3.impl.SystemResourceImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(SystemResourceLogoutUrlSetTestProfile.class)
public class SystemResourceLogoutUrlSetTest {

    @Inject
    SystemResourceImpl systemResource;

    @Test
    public void testLogoutUrlPresentWhenSet() {
        UserInterfaceConfig config = systemResource.getUIConfig();
        var options = config.getAuth().getOptions();

        // Proves the OIDC branch of uiAuthConfig() actually ran
        assertEquals("test-client", options.get("clientId"));
        assertEquals("https://example.com/logout", options.get("logoutUrl"));
    }
}
