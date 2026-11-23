package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.error.ContractStateConflictException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract status transitions and promotions are compare-and-set: a change validated against a state
 * that another request has changed since is rejected with 409.
 */
@QuarkusTest
public class ContractConcurrencyTest extends AbstractResourceTestBase {

    private static final String GROUP = "ContractConcurrencyTest";
    private static final String PREFIX = "contract.orders.";

    @Inject
    @Current
    RegistryStorage storage;

    private void createContractArtifact(String artifactId) throws Exception {
        createArtifact(GROUP, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}", ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).body(Map.of("labels", Map.of(PREFIX + "id", "orders")))
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(204);
        given().when().contentType(CT_JSON).body(Map.of("status", "DRAFT")).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/status").then()
                .statusCode(200);
    }

    private Map<String, String> labels(String artifactId) {
        return storage.getArtifactMetaData(GROUP, artifactId).getLabels();
    }

    @Test
    public void testStaleStatusTransitionIsRejected() throws Exception {
        String artifactId = "stale-status";
        createContractArtifact(artifactId);

        // Validated against STABLE, but the status is DRAFT
        assertThrows(ContractStateConflictException.class, () -> storage.transitionContractStatus(GROUP,
                artifactId, "STABLE", "DEPRECATED", PREFIX, "2026-11-16"));

        assertEquals("DRAFT", labels(artifactId).get(PREFIX + "status"));
        assertEquals(null, labels(artifactId).get(PREFIX + "lifecycle.deprecated-date"));
    }

    @Test
    public void testStalePromotionIsRejected() throws Exception {
        String artifactId = "stale-promotion";
        createContractArtifact(artifactId);
        storage.compareAndSetArtifactLabels(GROUP, artifactId, expectedStage(null), Map.of(PREFIX + "stage", "DEV"));

        // A second promotion validated when the contract had no stage
        assertThrows(ContractStateConflictException.class, () -> storage.compareAndSetArtifactLabels(GROUP,
                artifactId, expectedStage(null), Map.of(PREFIX + "stage", "DEV")));

        assertEquals("DEV", labels(artifactId).get(PREFIX + "stage"));
    }

    private static Map<String, String> expectedStage(String stage) {
        Map<String, String> expected = new HashMap<>();
        expected.put(PREFIX + "stage", stage);
        return expected;
    }

    @Test
    public void testConcurrentPromotionsOnlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                String artifactId = "concurrent-promotion-" + round;
                createContractArtifact(artifactId);

                CyclicBarrier barrier = new CyclicBarrier(2);
                List<Future<Integer>> responses = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    responses.add(executor.submit(() -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return given().when().contentType(CT_JSON)
                                .body(Map.of("contractId", "orders", "targetStage", "DEV"))
                                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/promote")
                                .statusCode();
                    }));
                }
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> response : responses) {
                    statuses.add(response.get(30, TimeUnit.SECONDS));
                }

                // One promotion wins; the other gets 409, whether it lost the compare-and-set or read the
                // stage after the winner committed
                assertEquals(List.of(200, 409), statuses.stream().sorted().toList(), "statuses " + statuses);
                given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                        .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata").then()
                        .statusCode(200).body("stage", equalTo("DEV"));
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
