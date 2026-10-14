package io.apicurio.registry.webhooks;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Calls each of the five webhook subscription operations once, for the auth-configuration tests.
 */
final class WebhookAuthTestSupport {

    static final String SOME_ID = "00000000-0000-0000-0000-000000000000";

    private WebhookAuthTestSupport() {
    }

    static Map<String, Object> createBody() {
        return Map.of("endpointUrl", "https://hooks.example.com/registry",
                "eventTypes", List.of("io.apicurio.registry.artifact.created"));
    }

    static Map<String, Object> updateBody(long expectedRevision) {
        return Map.of("eventTypes", List.of(), "enabled", false, "expectedRevision", expectedRevision);
    }

    /**
     * Asserts that every operation returns {@code expectedStatus}. Get, update and delete target
     * {@link #SOME_ID}, so a caller that got past authorization would see 404 instead.
     */
    static void assertEveryOperationReturns(URL webhooks, Supplier<RequestSpecification> caller,
            int expectedStatus) {
        String item = webhooks + "/" + SOME_ID;
        assertAll(
                () -> assertEquals(expectedStatus, caller.get().get(webhooks).statusCode(), "list"),
                () -> assertEquals(expectedStatus, caller.get().contentType(ContentType.JSON).body(createBody())
                        .post(webhooks).statusCode(), "create"),
                () -> assertEquals(expectedStatus, caller.get().get(item).statusCode(), "get"),
                () -> assertEquals(expectedStatus, caller.get().contentType(ContentType.JSON).body(updateBody(1))
                        .put(item).statusCode(), "update"),
                () -> assertEquals(expectedStatus, caller.get().queryParam("expectedRevision", 1)
                        .delete(item).statusCode(), "delete"));
    }
}
