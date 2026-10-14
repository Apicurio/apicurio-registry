package io.apicurio.registry.webhooks;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.BasicAuthWithPropertiesTestProfile;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Webhook subscription endpoints over HTTP, with basic auth, RBAC and admin-override enabled.
 * {@code alice} is an administrator, {@code bob1} a developer.
 */
@QuarkusTest
@TestProfile(BasicAuthWithPropertiesTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class WebhookSubscriptionResourceTest extends AbstractResourceTestBase {

    private static final String WEBHOOKS = "/registry/v3/admin/webhooks";
    private static final String CREATED = "io.apicurio.registry.artifact.created";
    private static final String VERSION_CREATED = "io.apicurio.registry.artifact.version.created";
    private static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";

    @InjectMock
    HostResolver hostResolver;

    @InjectMock
    WebhookRuntime runtime;

    /** The base class cleans up with this client before each test, so it must authenticate as an admin. */
    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth("alice", "alice"));
    }

    @BeforeEach
    void resolveEveryHostToAPublicAddress() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        when(hostResolver.resolve(anyString())).thenReturn(new InetAddress[] { publicAddress });
    }

    // ---- CRUD as an administrator ----

    @Test
    void fullLifecycleAsAdmin() {
        Response created = admin().contentType(ContentType.JSON)
                .body(Map.of("name", "orders", "endpointUrl", "https://hooks.example.com/registry",
                        "eventTypes", List.of(CREATED), "groupFilter", "DEFAULT",
                        "artifactTypeFilter", "AVRO", "signingSecretRef", "orders-key"))
                .post(WEBHOOKS);
        created.then().statusCode(201)
                .body("name", equalTo("orders"))
                .body("ownerId", equalTo("alice"))
                .body("endpointUrl", equalTo("https://hooks.example.com/registry"))
                .body("eventTypes", equalTo(List.of(CREATED)))
                .body("groupFilter", equalTo("default"))
                .body("artifactTypeFilter", equalTo("AVRO"))
                .body("enabled", equalTo(false))
                .body("signingSecretRef", equalTo("orders-key"))
                .body("revision", equalTo(1));
        String id = created.path("subscriptionId");
        created.then().header("Location", endsWith("/apis/registry/v3/admin/webhooks/" + id));

        admin().get(WEBHOOKS + "/" + id).then().statusCode(200)
                .body("subscriptionId", equalTo(id))
                .body("revision", equalTo(1));
        admin().get(WEBHOOKS).then().statusCode(200)
                .body("subscriptions.subscriptionId", hasItem(id));

        // PUT replaces the mutable fields: omitted optional fields are cleared.
        admin().contentType(ContentType.JSON)
                .body(Map.of("name", "orders v2", "eventTypes", List.of(), "enabled", false,
                        "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id)
                .then().statusCode(200)
                .body("name", equalTo("orders v2"))
                .body("eventTypes", equalTo(List.of()))
                .body("groupFilter", nullValue())
                .body("artifactTypeFilter", nullValue())
                .body("endpointUrl", equalTo("https://hooks.example.com/registry"))
                .body("signingSecretRef", equalTo("orders-key"))
                .body("revision", equalTo(2));

        // A stale revision is a conflict for both update and delete.
        admin().contentType(ContentType.JSON)
                .body(Map.of("eventTypes", List.of(), "enabled", false, "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).then().statusCode(409);
        admin().queryParam("expectedRevision", 1).delete(WEBHOOKS + "/" + id).then().statusCode(409);

        admin().queryParam("expectedRevision", 2).delete(WEBHOOKS + "/" + id).then().statusCode(204);
        admin().get(WEBHOOKS + "/" + id).then().statusCode(404);
        admin().get(WEBHOOKS).then().statusCode(200)
                .body("subscriptions.subscriptionId", not(hasItem(id)));
        admin().queryParam("expectedRevision", 3).delete(WEBHOOKS + "/" + id).then().statusCode(404);
    }

    @Test
    void listPagesInCreationOrderAndCountsAllActiveSubscriptions() {
        int before = admin().get(WEBHOOKS + "?limit=100").then().statusCode(200).extract().path("count");
        String first = createDisabled("https://hooks.example.com/page-1");
        String second = createDisabled("https://hooks.example.com/page-2");

        Response page = admin().queryParam("offset", before).queryParam("limit", 1).get(WEBHOOKS);
        page.then().statusCode(200)
                .body("count", equalTo(before + 2))
                .body("subscriptions.subscriptionId", equalTo(List.of(first)));
        admin().queryParam("offset", before + 1).queryParam("limit", 1).get(WEBHOOKS)
                .then().statusCode(200)
                .body("subscriptions.subscriptionId", equalTo(List.of(second)));
    }

    // ---- authorization ----

    @Test
    void nonAdminIsForbiddenOnEveryOperation() {
        String id = createDisabled("https://hooks.example.com/forbidden");
        RequestSpecification developer = given().auth().preemptive().basic("bob1", "bob1");

        developer.get(WEBHOOKS).then().statusCode(403);
        given().auth().preemptive().basic("bob1", "bob1").contentType(ContentType.JSON)
                .body(validCreateBody("https://hooks.example.com/x")).post(WEBHOOKS).then().statusCode(403);
        given().auth().preemptive().basic("bob1", "bob1").get(WEBHOOKS + "/" + id).then().statusCode(403);
        given().auth().preemptive().basic("bob1", "bob1").contentType(ContentType.JSON)
                .body(Map.of("eventTypes", List.of(), "enabled", false, "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).then().statusCode(403);
        given().auth().preemptive().basic("bob1", "bob1").queryParam("expectedRevision", 1)
                .delete(WEBHOOKS + "/" + id).then().statusCode(403);

        // Nothing changed.
        admin().get(WEBHOOKS + "/" + id).then().statusCode(200).body("revision", equalTo(1));
    }

    @Test
    void anonymousCallerIsUnauthenticatedOnEveryOperation() {
        given().get(WEBHOOKS).then().statusCode(401);
        given().contentType(ContentType.JSON).body(validCreateBody("https://hooks.example.com/x"))
                .post(WEBHOOKS).then().statusCode(401);
        given().get(WEBHOOKS + "/" + MISSING_ID).then().statusCode(401);
        given().contentType(ContentType.JSON)
                .body(Map.of("eventTypes", List.of(), "enabled", false, "expectedRevision", 1))
                .put(WEBHOOKS + "/" + MISSING_ID).then().statusCode(401);
        given().queryParam("expectedRevision", 1).delete(WEBHOOKS + "/" + MISSING_ID).then().statusCode(401);
    }

    // ---- validation ----

    @Test
    void invalidEndpointUrlIsRejectedWithoutEchoingIt() {
        Response response = admin().contentType(ContentType.JSON)
                .body(validCreateBody("http://user:topsecret@hooks.example.com/x")).post(WEBHOOKS);
        response.then().statusCode(400)
                .body("title", equalTo("endpointUrl: must be an absolute https URL"));
        assertFalse(response.asString().contains("topsecret"));
        assertFalse(response.asString().contains("hooks.example.com"));
    }

    @Test
    void endpointResolvingToAnInternalAddressIsRejected() throws Exception {
        when(hostResolver.resolve(anyString()))
                .thenReturn(new InetAddress[] { InetAddress.getByName("10.0.0.7") });
        admin().contentType(ContentType.JSON).body(validCreateBody("https://internal.example.com/x"))
                .post(WEBHOOKS)
                .then().statusCode(400)
                .body("title", equalTo("endpointUrl: must resolve only to public addresses"));
    }

    @Test
    void reservedAndDuplicateEventTypesAreRejected() {
        admin().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", "https://hooks.example.com/x",
                        "eventTypes", List.of("io.apicurio.registry.rule.violated")))
                .post(WEBHOOKS).then().statusCode(400)
                .body("title", equalTo("eventTypes: contains an event type that is reserved and not supported yet"));
        admin().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", "https://hooks.example.com/x",
                        "eventTypes", List.of(CREATED, CREATED)))
                .post(WEBHOOKS).then().statusCode(400)
                .body("title", equalTo("eventTypes: must not contain duplicates"));
    }

    @Test
    void outOfRangePagingAndRevisionAreRejected() {
        admin().queryParam("limit", 101).get(WEBHOOKS).then().statusCode(400);
        admin().queryParam("limit", 0).get(WEBHOOKS).then().statusCode(400);
        admin().queryParam("offset", -1).get(WEBHOOKS).then().statusCode(400);
        admin().queryParam("offset", "99999999999999999999").get(WEBHOOKS).then().statusCode(400);

        String id = createDisabled("https://hooks.example.com/revision");
        admin().queryParam("expectedRevision", 0).delete(WEBHOOKS + "/" + id).then().statusCode(400);
        admin().delete(WEBHOOKS + "/" + id).then().statusCode(400);
        admin().get(WEBHOOKS + "/" + id).then().statusCode(200);
    }

    @Test
    void malformedIdIsNotFound() {
        admin().get(WEBHOOKS + "/not-a-uuid").then().statusCode(404);
        admin().get(WEBHOOKS + "/1-1-1-1-1").then().statusCode(404);
        admin().get(WEBHOOKS + "/" + MISSING_ID).then().statusCode(404)
                .body("title", equalTo("No webhook subscription with this ID."));
    }

    @Test
    void updateCannotChangeImmutableFields() {
        String id = createDisabled("https://hooks.example.com/immutable");
        admin().contentType(ContentType.JSON)
                .body(Map.of("eventTypes", List.of(), "enabled", false, "expectedRevision", 1,
                        "endpointUrl", "https://attacker.example.com/x"))
                .put(WEBHOOKS + "/" + id).then().statusCode(400)
                .body("title", equalTo("endpointUrl: cannot be changed; create a new subscription instead"));
        admin().contentType(ContentType.JSON)
                .body(Map.of("eventTypes", List.of(), "enabled", false, "expectedRevision", 1,
                        "signingSecretRef", "other-key"))
                .put(WEBHOOKS + "/" + id).then().statusCode(400)
                .body("title", equalTo("signingSecretRef: cannot be changed; create a new subscription instead"));
        admin().get(WEBHOOKS + "/" + id).then().statusCode(200)
                .body("endpointUrl", equalTo("https://hooks.example.com/immutable"))
                .body("revision", equalTo(1));
    }

    @Test
    void unknownPropertyInCreateIsRejected() {
        admin().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", "https://hooks.example.com/x", "eventTypes", List.of(CREATED),
                        "ownerId", "someone-else"))
                .post(WEBHOOKS).then().statusCode(400)
                .body("title", equalTo("body: contains a field that is not allowed"));
    }

    @Test
    void unknownPropertyFromAnonymousCallerIsStillUnauthenticated() {
        given().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", "https://hooks.example.com/x", "eventTypes", List.of(CREATED),
                        "ownerId", "someone-else"))
                .post(WEBHOOKS).then().statusCode(401);
    }

    // ---- concurrent writes with the same revision ----

    @Test
    void concurrentUpdatesWithTheSameRevisionLetExactlyOneWin() throws Exception {
        String id = createDisabled("https://hooks.example.com/race-update");
        Supplier<Integer> update = () -> admin().contentType(ContentType.JSON)
                .body(Map.of("eventTypes", List.of(), "enabled", false, "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).statusCode();

        assertEquals(List.of(200, 409), raceSorted(update, update));
        admin().get(WEBHOOKS + "/" + id).then().statusCode(200).body("revision", equalTo(2));
    }

    @Test
    void concurrentUpdateAndDeleteWithTheSameRevisionLetExactlyOneWin() throws Exception {
        String id = createDisabled("https://hooks.example.com/race-delete");
        Supplier<Integer> update = () -> admin().contentType(ContentType.JSON)
                .body(Map.of("eventTypes", List.of(), "enabled", false, "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).statusCode();
        Supplier<Integer> delete = () -> admin().queryParam("expectedRevision", 1)
                .delete(WEBHOOKS + "/" + id).statusCode();

        List<Integer> statuses = raceSorted(update, delete);
        // Exactly one wins: the update (200) or the delete (204). If the delete wins, the update finds the
        // subscription gone (404); if the update wins, the delete finds a newer revision (409).
        assertEquals(1, statuses.stream().filter(code -> code == 200 || code == 204).count(), statuses::toString);
        assertEquals(1, statuses.stream().filter(code -> code == 404 || code == 409).count(), statuses::toString);
    }

    // ---- disabled by default; activation gate ----

    @Test
    void enablingIsRefusedWhileDeliveryIsUnavailable() {
        Map<String, Object> enabled = Map.of("endpointUrl", "https://hooks.example.com/x",
                "eventTypes", List.of(CREATED), "enabled", true, "signingSecretRef", "orders-key");
        admin().contentType(ContentType.JSON).body(enabled).post(WEBHOOKS).then().statusCode(409);

        String id = createDisabled("https://hooks.example.com/activate");
        admin().contentType(ContentType.JSON)
                .body(Map.of("eventTypes", List.of(), "enabled", true, "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).then().statusCode(409);
        admin().get(WEBHOOKS + "/" + id).then().statusCode(200)
                .body("enabled", equalTo(false)).body("revision", equalTo(1));
    }

    @Test
    void enablingNeedsAResolvableSigningKey() {
        when(runtime.isAvailable()).thenReturn(true);
        when(runtime.signingKeyResolves("orders-key")).thenReturn(true);

        admin().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", "https://hooks.example.com/x", "eventTypes", List.of(CREATED),
                        "enabled", true, "signingSecretRef", "unknown-key"))
                .post(WEBHOOKS).then().statusCode(409);
        admin().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", "https://hooks.example.com/x", "eventTypes", List.of(CREATED),
                        "enabled", true))
                .post(WEBHOOKS).then().statusCode(409);
        admin().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", "https://hooks.example.com/x", "eventTypes", List.of(CREATED),
                        "enabled", true, "signingSecretRef", "orders-key"))
                .post(WEBHOOKS).then().statusCode(201).body("enabled", equalTo(true));
    }

    @Test
    void createdSubscriptionIsDisabledWhenEnabledIsOmitted() {
        Response created = admin().contentType(ContentType.JSON)
                .body(validCreateBody("https://hooks.example.com/default-disabled")).post(WEBHOOKS);
        created.then().statusCode(201).body("enabled", equalTo(false));
        assertEquals(1, (int) created.path("revision"));
    }

    // ---- helpers ----

    /** Starts both calls at the same moment and returns their status codes, sorted. */
    private static List<Integer> raceSorted(Supplier<Integer> first, Supplier<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Supplier<Integer> call : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Race start signal was never given.");
                    }
                    return call.get();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            Collections.sort(statuses);
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private static RequestSpecification admin() {
        return given().auth().preemptive().basic("alice", "alice");
    }

    private static Map<String, Object> validCreateBody(String endpointUrl) {
        return Map.of("endpointUrl", endpointUrl, "eventTypes", List.of(VERSION_CREATED));
    }

    private static String createDisabled(String endpointUrl) {
        return admin().contentType(ContentType.JSON).body(validCreateBody(endpointUrl)).post(WEBHOOKS)
                .then().statusCode(201).extract().path("subscriptionId");
    }
}
