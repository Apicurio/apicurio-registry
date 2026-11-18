package io.apicurio.registry.cli.services;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class ClientTest {

    private static final String CONFIG_JSON = """
            {
              "installation-version": 1,
              "config": {},
              "context": {
                "test-context": {
                  "registryUrl": "http://localhost:8080/apis/registry/v3"
                }
              },
              "currentContext": "test-context"
            }""";

    @TempDir
    Path acrHome;

    @Inject
    Config config;

    @Inject
    Client client;

    @BeforeEach
    public void setUp() throws Exception {
        Files.writeString(acrHome.resolve("config.json"), CONFIG_JSON);
        config.reset();
        config.setAcrCurrentHomePath(acrHome);
        client.reset();
    }

    @AfterEach
    public void tearDown() {
        client.reset();
    }

    @Test
    public void testRegistryClientOptionsHaveRequestTimeoutConfigured() {
        RegistryClientOptions options = client.createRegistryClientOptions(false);
        assertThat(options.getConnectTimeoutMs()).isEqualTo(30_000);
        assertThat(options.getReadIdleTimeoutMs()).isEqualTo(120_000);
    }
}
