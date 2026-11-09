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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
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
    void createWithoutEventTypesIsRejectedAndCreatesNothing() {
        int before = admin().get(WEBHOOKS).then().statusCode(200).extract().path("count");
        admin().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", "https://hooks.example.com/x"))
                .post(WEBHOOKS).then().statusCode(400)
                .body("status", equalTo(400))
                .body("title", equalTo("eventTypes: is required"));
        admin().get(WEBHOOKS).then().statusCode(200).body("count", equalTo(before));
    }

    @Test
    void createWithEmptyEventTypesSubscribesToEveryType() {
        admin().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", "https://hooks.example.com/x", "eventTypes", List.of()))
                .post(WEBHOOKS).then().statusCode(201)
                .body("eventTypes", equalTo(List.of()));
    }

    @Test
    void updateWithoutEventTypesIsRejectedAndChangesNothing() {
        String id = createDisabled("https://hooks.example.com/x");
        admin().contentType(ContentType.JSON)
                .body(Map.of("name", "renamed", "enabled", false, "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).then().statusCode(400)
                .body("status", equalTo(400))
                .body("title", equalTo("eventTypes: is required"));
        admin().get(WEBHOOKS + "/" + id).then().statusCode(200)
                .body("revision", equalTo(1))
                .body("eventTypes", equalTo(List.of(VERSION_CREATED)))
                .body("name", nullValue());
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

        List<Integer> statuses = race(updateAtRevision1(id, "race-A", CREATED),
                updateAtRevision1(id, "race-B", VERSION_CREATED));

        List<Integer> sorted = new ArrayList<>(statuses);
        sorted.sort(null);
        assertEquals(List.of(200, 409), sorted, statuses::toString);
        // The stored state is exactly the winner's payload, written once.
        boolean firstWon = statuses.get(0) == 200;
        admin().get(WEBHOOKS + "/" + id).then().statusCode(200)
                .body("name", equalTo(firstWon ? "race-A" : "race-B"))
                .body("eventTypes", equalTo(List.of(firstWon ? CREATED : VERSION_CREATED)))
                .body("revision", equalTo(2));
    }

    @Test
    void concurrentUpdateAndDeleteWithTheSameRevisionLetExactlyOneWin() throws Exception {
        String id = createDisabled("https://hooks.example.com/race-delete");
        Supplier<Integer> delete = () -> admin().queryParam("expectedRevision", 1)
                .delete(WEBHOOKS + "/" + id).statusCode();

        List<Integer> statuses = race(updateAtRevision1(id, "race-update", CREATED), delete);
        int updateStatus = statuses.get(0);
        int deleteStatus = statuses.get(1);

        if (deleteStatus == 204) {
            // The delete won: the update found the subscription gone, and it stays gone.
            assertEquals(404, updateStatus, statuses::toString);
            admin().get(WEBHOOKS + "/" + id).then().statusCode(404);
        } else {
            // The update won: the delete found a newer revision, and the update's payload persisted.
            assertEquals(List.of(200, 409), statuses, statuses::toString);
            admin().get(WEBHOOKS + "/" + id).then().statusCode(200)
                    .body("name", equalTo("race-update"))
                    .body("eventTypes", equalTo(List.of(CREATED)))
                    .body("revision", equalTo(2));
        }
    }

    // ---- stale revisions leave the stored state untouched ----

    @Test
    void staleUpdateWithDifferentValuesChangesNothing() {
        String id = admin().contentType(ContentType.JSON)
                .body(Map.of("name", "original", "endpointUrl", "https://hooks.example.com/stale-update",
                        "eventTypes", List.of(CREATED), "groupFilter", "orders",
                        "artifactIdFilter", "orders-value", "artifactTypeFilter", "AVRO"))
                .post(WEBHOOKS).then().statusCode(201).extract().path("subscriptionId");
        // Move to revision 2 so that revision 1 is genuinely stale.
        admin().contentType(ContentType.JSON)
                .body(Map.of("name", "current", "eventTypes", List.of(CREATED), "groupFilter", "orders",
                        "artifactIdFilter", "orders-value", "artifactTypeFilter", "AVRO", "enabled", false,
                        "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).then().statusCode(200).body("revision", equalTo(2));
        Map<String, Object> before = getState(id);

        // Every mutable field differs from the stored value.
        admin().contentType(ContentType.JSON)
                .body(Map.of("name", "stale", "eventTypes", List.of(VERSION_CREATED), "groupFilter", "payments",
                        "artifactIdFilter", "payments-value", "artifactTypeFilter", "JSON", "enabled", false,
                        "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).then().statusCode(409);

        assertEquals(before, getState(id));
        assertEquals("current", before.get("name"));
        assertEquals(2, before.get("revision"));
    }

    @Test
    void staleDeleteOfAnEnabledSubscriptionChangesNothing() {
        when(runtime.isAvailable()).thenReturn(true);
        when(runtime.signingKeyResolves("orders-key")).thenReturn(true);
        String id = admin().contentType(ContentType.JSON)
                .body(Map.of("name", "original", "endpointUrl", "https://hooks.example.com/stale-delete",
                        "eventTypes", List.of(CREATED), "enabled", true, "signingSecretRef", "orders-key"))
                .post(WEBHOOKS).then().statusCode(201).body("enabled", equalTo(true))
                .extract().path("subscriptionId");
        admin().contentType(ContentType.JSON)
                .body(Map.of("name", "current", "eventTypes", List.of(CREATED), "enabled", true,
                        "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).then().statusCode(200).body("revision", equalTo(2));
        Map<String, Object> before = getState(id);

        admin().queryParam("expectedRevision", 1).delete(WEBHOOKS + "/" + id).then().statusCode(409);

        // Starting enabled means an accidental disable would show up here, not only an accidental delete.
        assertEquals(before, getState(id));
        assertEquals(true, before.get("enabled"));
        assertEquals(2, before.get("revision"));
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
    void putEnablesASubscriptionWhoseStoredSigningKeyResolves() {
        when(runtime.isAvailable()).thenReturn(true);
        when(runtime.signingKeyResolves("orders-key")).thenReturn(true);
        String id = createDisabled("https://hooks.example.com/put-activate", "orders-key");

        admin().contentType(ContentType.JSON)
                .body(Map.of("eventTypes", List.of(CREATED), "enabled", true, "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id)
                .then().statusCode(200)
                .body("enabled", equalTo(true))
                .body("signingSecretRef", equalTo("orders-key"))
                .body("revision", equalTo(2));

        admin().get(WEBHOOKS + "/" + id).then().statusCode(200)
                .body("enabled", equalTo(true)).body("revision", equalTo(2));
        // The PUT body carries no key reference, so the decision used the stored one.
        verify(runtime).signingKeyResolves("orders-key");
    }

    @Test
    void putActivationIsRefusedWhenNoSigningKeyIsStored() {
        when(runtime.isAvailable()).thenReturn(true);
        String id = createDisabled("https://hooks.example.com/put-no-key");

        admin().contentType(ContentType.JSON)
                .body(Map.of("name", "renamed", "eventTypes", List.of(CREATED), "enabled", true,
                        "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).then().statusCode(409);

        admin().get(WEBHOOKS + "/" + id).then().statusCode(200)
                .body("enabled", equalTo(false))
                .body("name", nullValue())
                .body("signingSecretRef", nullValue())
                .body("revision", equalTo(1));
    }

    @Test
    void putActivationIsRefusedWhenTheStoredSigningKeyDoesNotResolve() {
        when(runtime.isAvailable()).thenReturn(true);
        when(runtime.signingKeyResolves("orders-key")).thenReturn(true);
        String id = createDisabled("https://hooks.example.com/put-unknown-key", "unknown-key");

        admin().contentType(ContentType.JSON)
                .body(Map.of("name", "renamed", "eventTypes", List.of(CREATED), "enabled", true,
                        "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).then().statusCode(409);

        admin().get(WEBHOOKS + "/" + id).then().statusCode(200)
                .body("enabled", equalTo(false))
                .body("name", nullValue())
                .body("signingSecretRef", equalTo("unknown-key"))
                .body("revision", equalTo(1));
        verify(runtime).signingKeyResolves("unknown-key");
    }

    @Test
    void createdSubscriptionIsDisabledWhenEnabledIsOmitted() {
        Response created = admin().contentType(ContentType.JSON)
                .body(validCreateBody("https://hooks.example.com/default-disabled")).post(WEBHOOKS);
        created.then().statusCode(201).body("enabled", equalTo(false));
        assertEquals(1, (int) created.path("revision"));
    }

    // ---- helpers ----

    /**
     * Runs both calls on separate threads, released together only once both threads are waiting, and returns
     * their status codes in argument order. Unfinished calls are cancelled and awaited on any failure.
     */
    private static List<Integer> race(Supplier<Integer> first, Supplier<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            for (Supplier<Integer> call : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Race start signal was never given.");
                    }
                    return call.get();
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "Both race workers must be waiting before the start.");
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            futures.forEach(future -> future.cancel(true));
            executor.shutdownNow();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Race workers did not stop.");
            }
        }
    }

    /** A disabled-state PUT at revision 1 whose name and event type identify which request persisted. */
    private static Supplier<Integer> updateAtRevision1(String id, String name, String eventType) {
        return () -> admin().contentType(ContentType.JSON)
                .body(Map.of("name", name, "eventTypes", List.of(eventType), "enabled", false,
                        "expectedRevision", 1))
                .put(WEBHOOKS + "/" + id).statusCode();
    }

    /** The full stored subscription as returned by GET. */
    private static Map<String, Object> getState(String id) {
        return admin().get(WEBHOOKS + "/" + id).then().statusCode(200).extract().jsonPath().getMap("$");
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

    private static String createDisabled(String endpointUrl, String signingSecretRef) {
        return admin().contentType(ContentType.JSON)
                .body(Map.of("endpointUrl", endpointUrl, "eventTypes", List.of(VERSION_CREATED),
                        "signingSecretRef", signingSecretRef))
                .post(WEBHOOKS)
                .then().statusCode(201).body("enabled", equalTo(false)).extract().path("subscriptionId");
    }
}
