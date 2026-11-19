package io.apicurio.registry.noprofile.http;

import io.apicurio.registry.http.HttpClientException;
import io.apicurio.registry.http.HttpClientService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A webhook that cannot be reached must fail within the call's timeout, rather than after every retry and
 * its exponential backoff (about 25 seconds per call before).
 */
@QuarkusTest
class WebhookRetryBudgetTest {

    @Inject
    HttpClientService httpClientService;

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void unreachableWebhookFailsWithinTheRetryBudget() throws Exception {
        String url = "http://localhost:" + unusedPort() + "/webhook";

        long start = System.nanoTime();
        assertThrows(HttpClientException.class, () -> httpClientService.post(url, Map.of(), Map.class));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        // The retries stop after 10 seconds; the next backoff delay alone is 6.4 seconds
        assertTrue(elapsedMillis < 18_000, "took " + elapsedMillis + " ms");
    }
}
