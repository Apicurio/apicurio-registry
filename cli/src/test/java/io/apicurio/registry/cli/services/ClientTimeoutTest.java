package io.apicurio.registry.cli.services;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Registry calls must not wait forever on a connection that stalls (a test once hung for 30 minutes).
 */
class ClientTimeoutTest {

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() {
        vertx.close().toCompletionStage().toCompletableFuture().join();
    }

    @Test
    void registryCallsHaveConnectAndReadTimeouts() {
        var options = Client.registryClientOptions("http://localhost:8080/apis/registry/v3", vertx, false, true);
        assertEquals(30_000, options.getConnectTimeoutMs());
        assertEquals(120_000, options.getReadIdleTimeoutMs());
    }

    @Test
    void longRequestsHaveALongerReadTimeout() {
        var options = Client.registryClientOptions("http://localhost:8080/apis/registry/v3", vertx, false, false);
        assertEquals(30_000, options.getConnectTimeoutMs());
        assertEquals(3_600_000, options.getReadIdleTimeoutMs());
    }
}
