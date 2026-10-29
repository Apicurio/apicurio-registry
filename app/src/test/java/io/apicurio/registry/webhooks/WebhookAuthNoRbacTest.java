package io.apicurio.registry.webhooks;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Authentication on but neither RBAC nor admin-override: even a user holding the admin role is refused,
 * because nothing in this configuration decides who is an administrator.
 */
@QuarkusTest
@TestProfile(WebhookAuthNoRbacTest.Profile.class)
public class WebhookAuthNoRbacTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return WebhookAuthProfiles.basicAuth(Map.of(
                    "apicurio.auth.role-based-authorization", "false",
                    "apicurio.auth.admin-override.enabled", "false",
                    "apicurio.auth.owner-only-authorization", "false"));
        }
    }

    @TestHTTPResource("/apis/registry/v3/admin/webhooks")
    URL webhooks;

    @Test
    void adminRoleHolderIsForbidden() {
        WebhookAuthTestSupport.assertEveryOperationReturns(webhooks,
                () -> given().auth().preemptive().basic("alice", "alice"), 403);
    }
}
