package io.apicurio.registry.cli.services;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class ClientTimeoutTest {

    @Inject
    Client client;

    @Test
    void registryClientOptionsCarryTheConnectAndReadIdleTimeouts() {
        final var options = client.newRegistryClientOptions("http://localhost:8080/apis/registry/v3");

        assertThat(options.getConnectTimeoutMs()).isEqualTo(60_000L);
        assertThat(options.getReadIdleTimeoutMs()).isEqualTo(180_000L);
    }
}
