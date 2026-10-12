package io.apicurio.registry.client;

import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server that accepts a request and never answers must fail the call once the read idle timeout
 * passes, instead of leaving the caller waiting on the response for ever.
 */
class RegistryClientRequestTimeoutTest {

    private static Vertx vertx;
    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startServerThatNeverResponds() throws Exception {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer()
                .requestHandler(request -> {
                    // Accept the request and send nothing back.
                })
                .listen(0)
                .toCompletionStage()
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
        port = server.actualPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void requestFailsOnceTheReadIdleTimeoutPasses() {
        RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl("http://localhost:" + port + "/apis/registry/v3")
                .vertx(vertx)
                .requestTimeout(5_000, 1_000));

        long start = System.nanoTime();
        assertThrows(RuntimeException.class, () -> client.groups().get());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMillis < 15_000, "the call took " + elapsedMillis + " ms");
    }
}
