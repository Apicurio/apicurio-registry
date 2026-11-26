package io.apicurio.registry.webhooks;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URL;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Admin-override alone (RBAC off) is enough to identify administrators: the override admin may manage
 * subscriptions, anyone else may not.
 */
@QuarkusTest
@TestProfile(WebhookAuthAdminOverrideTest.Profile.class)
public class WebhookAuthAdminOverrideTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return WebhookAuthProfiles.basicAuth(Map.of(
                    "apicurio.auth.role-based-authorization", "false",
                    "apicurio.auth.admin-override.enabled", "true",
                    "apicurio.auth.admin-override.from", "token",
                    "apicurio.auth.admin-override.type", "role",
                    "apicurio.auth.admin-override.role", "sr-admin",
                    "apicurio.auth.owner-only-authorization", "false"));
        }
    }

    @TestHTTPResource("/apis/registry/v3/admin/webhooks")
    URL webhooks;

    @InjectMock
    HostResolver hostResolver;

    @Test
    void overrideAdminCanManageSubscriptions() throws Exception {
        when(hostResolver.resolve(anyString()))
                .thenReturn(new InetAddress[] { InetAddress.getByName("93.184.216.34") });

        String id = given().auth().preemptive().basic("alice", "alice").contentType(ContentType.JSON)
                .body(WebhookAuthTestSupport.createBody()).post(webhooks)
                .then().statusCode(201).body("ownerId", equalTo("alice"))
                .extract().path("subscriptionId");
        String item = webhooks + "/" + id;
        given().auth().preemptive().basic("alice", "alice").get(item).then().statusCode(200);
        given().auth().preemptive().basic("alice", "alice").get(webhooks).then().statusCode(200);
        given().auth().preemptive().basic("alice", "alice").contentType(ContentType.JSON)
                .body(WebhookAuthTestSupport.updateBody(1)).put(item).then().statusCode(200)
                .body("revision", equalTo(2));
        given().auth().preemptive().basic("alice", "alice").queryParam("expectedRevision", 2)
                .delete(item).then().statusCode(204);
    }

    @Test
    void nonAdminIsForbidden() {
        WebhookAuthTestSupport.assertEveryOperationReturns(webhooks,
                () -> given().auth().preemptive().basic("bob1", "bob1"), 403);
    }

    @Test
    void anonymousCallerIsUnauthenticated() {
        WebhookAuthTestSupport.assertEveryOperationReturns(webhooks, () -> given(), 401);
    }
}
