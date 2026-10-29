package io.apicurio.registry.webhooks;

import io.apicurio.registry.rbac.MockAuditLogService;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.BasicAuthWithPropertiesTestProfile;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The audit log must record webhook operations without their endpoint URL, filters or signing key name.
 */
@QuarkusTest
@TestProfile(BasicAuthWithPropertiesTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class WebhookAuditTest {

    private static final String SECRET_HOST = "audit-marker-host.example.com";
    private static final String SECRET_GROUP = "audit-marker-group";
    private static final String SECRET_KEY = "audit-marker-key";

    @TestHTTPResource("/apis/registry/v3/admin/webhooks")
    URL webhooks;

    @Inject
    MockAuditLogService auditLogService;

    @InjectMock
    HostResolver hostResolver;

    @BeforeEach
    void setUp() throws Exception {
        when(hostResolver.resolve(anyString()))
                .thenReturn(new InetAddress[] { InetAddress.getByName("93.184.216.34") });
        auditLogService.resetAuditLogs();
    }

    @Test
    void failedAndSuccessfulOperationsDoNotLogRequestValues() {
        // Fails validation (http, not https).
        given().auth().preemptive().basic("alice", "alice").contentType(ContentType.JSON)
                .body(body("http://" + SECRET_HOST + "/x")).post(webhooks).then().statusCode(400);
        // Succeeds.
        String id = given().auth().preemptive().basic("alice", "alice").contentType(ContentType.JSON)
                .body(body("https://" + SECRET_HOST + "/x")).post(webhooks)
                .then().statusCode(201).extract().path("subscriptionId");
        given().auth().preemptive().basic("alice", "alice").get(webhooks + "/" + id).then().statusCode(200);

        List<Map<String, String>> webhookEntries = auditLogService.getAuditLogs().stream()
                .filter(entry -> entry.getOrDefault("action", "").contains("WebhookSubscription"))
                .collect(Collectors.toList());
        assertEquals(List.of("createWebhookSubscription:failure", "createWebhookSubscription:success",
                "getWebhookSubscription:success"),
                webhookEntries.stream().map(e -> e.get("action") + ":" + e.get("result"))
                        .collect(Collectors.toList()));

        for (Map<String, String> entry : webhookEntries) {
            String logged = entry.toString();
            assertFalse(logged.contains(SECRET_HOST), logged);
            assertFalse(logged.contains(SECRET_GROUP), logged);
            assertFalse(logged.contains(SECRET_KEY), logged);
        }
        assertEquals("endpointUrl: must be an absolute https URL", webhookEntries.get(0).get("error_msg"));
        assertEquals(id, webhookEntries.get(2).get("subscription_id"));
        assertEquals("alice", webhookEntries.get(2).get("principal_id"));
    }

    private static Map<String, Object> body(String endpointUrl) {
        return Map.of("endpointUrl", endpointUrl, "eventTypes", List.of("io.apicurio.registry.artifact.created"),
                "groupFilter", SECRET_GROUP, "signingSecretRef", SECRET_KEY);
    }
}
