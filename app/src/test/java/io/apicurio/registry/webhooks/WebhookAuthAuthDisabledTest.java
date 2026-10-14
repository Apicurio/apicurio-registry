package io.apicurio.registry.webhooks;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static io.restassured.RestAssured.given;

/**
 * With authentication disabled (the default configuration) every other endpoint is open, but webhook
 * management must be refused: the registry cannot tell who an administrator is.
 */
@QuarkusTest
public class WebhookAuthAuthDisabledTest {

    @TestHTTPResource("/apis/registry/v3/admin/webhooks")
    URL webhooks;

    @Test
    void everyOperationIsForbidden() {
        WebhookAuthTestSupport.assertEveryOperationReturns(webhooks, () -> given(), 403);
    }

    @Test
    void otherAdminEndpointsStayOpen() {
        // Shows the configuration really is open, so the 403s above come from the webhook gate.
        given().get(webhooks.toString().replace("/webhooks", "/rules")).then().statusCode(200);
    }
}
