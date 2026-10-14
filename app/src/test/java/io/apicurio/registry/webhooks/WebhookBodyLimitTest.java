package io.apicurio.registry.webhooks;

import io.apicurio.registry.rest.v3.impl.WebhookRequestBodyReaderInterceptor;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.BasicAuthWithPropertiesTestProfile;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Request body limits and malformed bodies on the webhook subscription endpoints. Body errors are reported
 * only after authorization, and never quote what was sent.
 */
@QuarkusTest
@TestProfile(BasicAuthWithPropertiesTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class WebhookBodyLimitTest {

    private static final int MAX = WebhookRequestBodyReaderInterceptor.MAX_BODY_BYTES;
    private static final String MALFORMED = "body: must be a JSON object matching the request schema";
    private static final String MARKER = "body-limit-marker.example.com";
    private static final String VALID_CREATE = "{\"endpointUrl\":\"https://hooks.example.com/limit\","
            + "\"eventTypes\":[\"io.apicurio.registry.artifact.created\"]}";

    @TestHTTPResource("/apis/registry/v3/admin/webhooks")
    URL webhooks;

    @InjectMock
    HostResolver hostResolver;

    @BeforeEach
    void resolveEveryHostToAPublicAddress() throws Exception {
        when(hostResolver.resolve(anyString()))
                .thenReturn(new InetAddress[] { InetAddress.getByName("93.184.216.34") });
    }

    // ---- size limit ----

    @Test
    void bodyOfExactlyTheLimitIsAccepted() {
        String body = padTo(VALID_CREATE, MAX);
        assertEquals(MAX, body.getBytes(StandardCharsets.UTF_8).length);
        admin().contentType(ContentType.JSON).body(body).post(webhooks).then().statusCode(201);
    }

    @Test
    void bodyOverTheLimitIsRejectedOnCreateAndUpdate() {
        String oversized = padTo(VALID_CREATE, MAX + 1);
        admin().contentType(ContentType.JSON).body(oversized).post(webhooks)
                .then().statusCode(413).body("title", equalTo("body: must be at most " + MAX + " bytes"));

        String id = admin().contentType(ContentType.JSON).body(VALID_CREATE).post(webhooks)
                .then().statusCode(201).extract().path("subscriptionId");
        String oversizedUpdate = padTo("{\"eventTypes\":[],\"enabled\":false,\"expectedRevision\":1}", MAX + 1);
        admin().contentType(ContentType.JSON).body(oversizedUpdate).put(webhooks + "/" + id)
                .then().statusCode(413);
        admin().get(webhooks + "/" + id).then().statusCode(200).body("revision", equalTo(1));
    }

    @Test
    void oversizedBodyStillGetsAuthorizationErrorsFirst() {
        String oversized = padTo(VALID_CREATE, MAX + 1);
        given().contentType(ContentType.JSON).body(oversized).post(webhooks).then().statusCode(401);
        given().auth().preemptive().basic("bob1", "bob1").contentType(ContentType.JSON).body(oversized)
                .post(webhooks).then().statusCode(403);
    }

    // ---- malformed bodies ----

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"endpointUrl\":\"https://" + MARKER + "/x\",",
            "{\"endpointUrl\":\"https://hooks.example.com/x\",\"eventTypes\":[],\"enabled\":\"https://" + MARKER + "\"}",
            "{\"endpointUrl\":[\"https://" + MARKER + "\"],\"eventTypes\":[]}",
            "[\"https://" + MARKER + "\"]",
            "\"https://" + MARKER + "\"" })
    void malformedCreateBodyIsRejectedWithoutQuotingIt(String body) {
        Response response = admin().contentType(ContentType.JSON).body(body).post(webhooks);
        response.then().statusCode(400).body("title", equalTo(MALFORMED));
        assertFalse(response.asString().contains(MARKER), response.asString());
    }

    @Test
    void malformedUpdateBodyIsRejectedWithoutQuotingIt() {
        String id = admin().contentType(ContentType.JSON).body(VALID_CREATE).post(webhooks)
                .then().statusCode(201).extract().path("subscriptionId");
        Response response = admin().contentType(ContentType.JSON)
                .body("{\"eventTypes\":[],\"enabled\":false,\"expectedRevision\":\"" + MARKER + "\"}")
                .put(webhooks + "/" + id);
        response.then().statusCode(400).body("title", equalTo(MALFORMED));
        assertFalse(response.asString().contains(MARKER), response.asString());
    }

    @Test
    void emptyBodyIsRejected() {
        admin().contentType(ContentType.JSON).body("").post(webhooks)
                .then().statusCode(400).body("title", equalTo(MALFORMED));
    }

    @Test
    void malformedBodyStillGetsAuthorizationErrorsFirst() {
        given().contentType(ContentType.JSON).body("{\"endpointUrl\":").post(webhooks).then().statusCode(401);
        given().auth().preemptive().basic("bob1", "bob1").contentType(ContentType.JSON)
                .body("{\"endpointUrl\":").post(webhooks).then().statusCode(403);
    }

    private static RequestSpecification admin() {
        return given().auth().preemptive().basic("alice", "alice");
    }

    /** Pads a JSON object with trailing whitespace (still valid JSON) to exactly {@code size} bytes. */
    private static String padTo(String json, int size) {
        return json + " ".repeat(size - json.getBytes(StandardCharsets.UTF_8).length);
    }
}
