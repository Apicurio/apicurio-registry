package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class AccessLogTest extends AbstractResourceTestBase {

    @Test
    public void testSuccessfulRequestLogsResponseTime() {
        assertResponseTimeLogged("/registry/v3/system/info", 200);
    }

    @Test
    public void testNotFoundRequestLogsResponseTime() {
        assertResponseTimeLogged("/registry/v3/groups/" + UUID.randomUUID() + "/artifacts/missing", 404);
    }

    private void assertResponseTimeLogged(String path, int status) {
        String userAgent = "AccessLogTest-" + UUID.randomUUID();
        Queue<String> messages = new ConcurrentLinkedQueue<>();
        Logger logger = Logger.getLogger("io.quarkus.http.access-log");
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = record.getMessage();
                if (message.contains(userAgent)) {
                    messages.add(message);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            given().header("User-Agent", userAgent)
                    .when().get(path)
                    .then().statusCode(status);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                String message = messages.peek();
                assertTrue(message != null, "Expected an access-log entry for " + userAgent);
                assertTrue(message.contains("method=\"GET\""), message);
                assertTrue(message.contains("response_code=\"" + status + "\""), message);
                // Zero milliseconds is valid for fast requests; '-' is not a recorded duration.
                assertTrue(message.matches(".*response_time=\"[0-9]+\".*"), message);
            });
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
    }
}
