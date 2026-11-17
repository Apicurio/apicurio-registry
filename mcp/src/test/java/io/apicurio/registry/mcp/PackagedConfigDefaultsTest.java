package io.apicurio.registry.mcp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks the packaged configuration the server starts with outside tests. The test profile sets its
 * own OIDC values, so a {@code @QuarkusTest} cannot catch a wrong production default.
 */
class PackagedConfigDefaultsTest {

    /**
     * With the OIDC tenant enabled by default, stdio mode fails at startup asking for
     * {@code quarkus.oidc.auth-server-url}. HTTP mode turns OIDC on explicitly.
     */
    @Test
    void oidcIsOffUnlessHttpModeTurnsItOn() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(in, "application.properties should be on the classpath");
            properties.load(in);
        }

        assertEquals("false", properties.getProperty("quarkus.oidc.tenant-enabled"));
    }
}
