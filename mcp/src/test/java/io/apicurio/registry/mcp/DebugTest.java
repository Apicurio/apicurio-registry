package io.apicurio.registry.mcp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QuarkusTest
public class DebugTest {

    private static final Logger log = LoggerFactory.getLogger(DebugTest.class);

    @Inject
    RegistryService service;

    @Inject
    Utils utils;

    @Disabled("Used for debugging, not intended to run during the build.")
    @Test
    public void test_getServerInfo() {
        var info = service.getServerInfo();
        log.debug("Server info: {}", utils.toPrettyJson(info));
    }
}
