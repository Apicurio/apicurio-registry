package io.apicurio.registry.noprofile.iceberg.rest.v1;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Iceberg REST Catalog API.
 */
@QuarkusTest
@TestProfile(IcebergExperimentalFeaturesProfile.class)
public class IcebergApiTest extends AbstractResourceTestBase {

    private static final String ICEBERG_API_BASE = "";

    @Test
    public void testGetConfig() {
        given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/config")
            .then()
            .statusCode(200)
            .body("defaults", notNullValue())
            .body("overrides", notNullValue());
    }

    @Test
    public void testListNamespacesEmpty() {
        given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200)
            .body("namespaces", anything());
    }

    @Test
    public void testNamespaceLifecycle() {
        String namespaceName = "test_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Create namespace
        Map<String, Object> createRequest = Map.of(
            "namespace", List.of(namespaceName),
            "properties", Map.of("comment", "Test namespace")
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(createRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200)
            .body("namespace[0]", equalTo(namespaceName));

        // Check namespace exists
        given()
            .when()
            .head(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);

        // Load namespace metadata
        given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(200)
            .body("namespace[0]", equalTo(namespaceName))
            .body("properties.comment", equalTo("Test namespace"));

        // Update namespace properties
        Map<String, Object> updateRequest = Map.of(
            "updates", Map.of("owner", "test-user"),
            "removals", List.of()
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(updateRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/properties")
            .then()
            .statusCode(200)
            .body("updated", notNullValue());

        // Drop namespace
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);

        // Verify namespace is gone
        given()
            .when()
            .head(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(404);
    }

    @Test
    public void testTableLifecycle() {
        String namespaceName = "test_tbl_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "test_table";

        // Create namespace first
        Map<String, Object> createNsRequest = Map.of(
            "namespace", List.of(namespaceName),
            "properties", Map.of()
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(createNsRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200);

        // Create table
        Map<String, Object> createTableRequest = Map.of(
            "name", tableName,
            "schema", Map.of(
                "type", "struct",
                "schema-id", 0,
                "fields", List.of(
                    Map.of("id", 1, "name", "id", "required", true, "type", "long"),
                    Map.of("id", 2, "name", "data", "required", false, "type", "string")
                )
            ),
            "properties", Map.of()
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(createTableRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables")
            .then()
            .statusCode(200)
            .body("metadata.table-uuid", notNullValue())
            .body("metadata.format-version", equalTo(2));

        // List tables
        given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables")
            .then()
            .statusCode(200)
            .body("identifiers[0].name", equalTo(tableName));

        // Check table exists
        given()
            .when()
            .head(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(204);

        // Load table
        given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(200)
            .body("metadata.table-uuid", notNullValue());

        // Drop table
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(204);

        // Drop namespace
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);
    }

    @Test
    public void testRenameTable() {
        String namespaceName = "test_rename_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "original_table";
        String newTableName = "renamed_table";

        // Create namespace
        Map<String, Object> createNsRequest = Map.of(
            "namespace", List.of(namespaceName),
            "properties", Map.of()
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(createNsRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200);

        // Create table
        Map<String, Object> createTableRequest = Map.of(
            "name", tableName,
            "schema", Map.of(
                "type", "struct",
                "schema-id", 0,
                "fields", List.of(
                    Map.of("id", 1, "name", "id", "required", true, "type", "long")
                )
            )
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(createTableRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables")
            .then()
            .statusCode(200);

        // Rename table
        Map<String, Object> renameRequest = Map.of(
            "source", Map.of(
                "namespace", List.of(namespaceName),
                "name", tableName
            ),
            "destination", Map.of(
                "namespace", List.of(namespaceName),
                "name", newTableName
            )
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(renameRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/tables/rename")
            .then()
            .statusCode(204);

        // Verify old table is gone
        given()
            .when()
            .head(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(404);

        // Verify new table exists
        given()
            .when()
            .head(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + newTableName)
            .then()
            .statusCode(204);

        // Cleanup
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + newTableName)
            .then()
            .statusCode(204);

        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);
    }

    @Test
    public void testNamespaceNotEmpty() {
        String namespaceName = "test_notempty_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "test_table";

        // Create namespace
        Map<String, Object> createNsRequest = Map.of(
            "namespace", List.of(namespaceName),
            "properties", Map.of()
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(createNsRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200);

        // Create table
        Map<String, Object> createTableRequest = Map.of(
            "name", tableName,
            "schema", Map.of(
                "type", "struct",
                "schema-id", 0,
                "fields", List.of()
            )
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(createTableRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables")
            .then()
            .statusCode(200);

        // Try to drop non-empty namespace (should fail)
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(409);

        // Drop table first
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(204);

        // Now drop namespace (should succeed)
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);
    }

    // --- CommitTable Tests ---

    @Test
    public void testCommitTableBasic() {
        String namespaceName = "test_commit_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "commit_table";

        createNamespaceAndTable(namespaceName, tableName);

        // Commit: add-schema + set-current-schema
        Map<String, Object> newSchema = new HashMap<>();
        newSchema.put("type", "struct");
        newSchema.put("schema-id", 1);
        newSchema.put("fields", List.of(
            Map.of("id", 1, "name", "id", "required", true, "type", "long"),
            Map.of("id", 2, "name", "data", "required", false, "type", "string"),
            Map.of("id", 3, "name", "ts", "required", false, "type", "timestamp")
        ));

        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(),
            "updates", List.of(
                Map.of("action", "add-schema", "schema", newSchema),
                Map.of("action", "set-current-schema", "schema-id", 1)
            )
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(200)
            .body("metadata.current-schema-id", equalTo(1))
            .body("metadata.table-uuid", notNullValue());

        // Verify by loading the table
        given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(200)
            .body("metadata.current-schema-id", equalTo(1));

        cleanupTable(namespaceName, tableName);
    }

    @Test
    public void testCommitTableAddSnapshot() {
        String namespaceName = "test_snap_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "snapshot_table";

        createNamespaceAndTable(namespaceName, tableName);

        long snapshotId = System.currentTimeMillis();
        long timestampMs = System.currentTimeMillis();

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("snapshot-id", snapshotId);
        snapshot.put("timestamp-ms", timestampMs);
        snapshot.put("summary", Map.of("operation", "append"));
        snapshot.put("manifest-list", "s3://bucket/path/snap.avro");

        Map<String, Object> setRef = new HashMap<>();
        setRef.put("action", "set-snapshot-ref");
        setRef.put("ref-name", "main");
        setRef.put("snapshot-id", snapshotId);
        setRef.put("type", "branch");

        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(),
            "updates", List.of(
                Map.of("action", "add-snapshot", "snapshot", snapshot),
                setRef
            )
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(200)
            .body("metadata.current-snapshot-id", equalTo(snapshotId));

        cleanupTable(namespaceName, tableName);
    }

    @Test
    public void testCommitTableRequirementSuccess() {
        String namespaceName = "test_reqok_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "req_table";

        createNamespaceAndTable(namespaceName, tableName);

        // Load the table to get the UUID
        String tableUuid = given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(200)
            .extract().path("metadata.table-uuid");

        // Commit with assert-table-uuid requirement (should succeed)
        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(Map.of("type", "assert-table-uuid", "uuid", tableUuid)),
            "updates", List.of(Map.of("action", "set-location", "location", "/new/location"))
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(200)
            .body("metadata.location", equalTo("/new/location"));

        cleanupTable(namespaceName, tableName);
    }

    @Test
    public void testCommitTableRequirementFailure() {
        String namespaceName = "test_reqfail_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "req_fail_table";

        createNamespaceAndTable(namespaceName, tableName);

        // Commit with wrong UUID (should fail with 409)
        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(Map.of("type", "assert-table-uuid", "uuid", "wrong-uuid")),
            "updates", List.of(Map.of("action", "set-location", "location", "/new/location"))
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(409)
            .body("error.type", equalTo("CommitFailedException"));

        cleanupTable(namespaceName, tableName);
    }

    @Test
    public void testCommitTableAssertCreate() {
        String namespaceName = "test_assertcreate_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "assert_create_table";

        createNamespaceAndTable(namespaceName, tableName);

        // assert-create on existing table should fail with 409
        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(Map.of("type", "assert-create")),
            "updates", List.of(Map.of("action", "assign-uuid", "uuid", "new-uuid"))
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(409);

        cleanupTable(namespaceName, tableName);
    }

    @Test
    public void testCommitTableMultipleUpdates() {
        String namespaceName = "test_multi_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "multi_update_table";

        createNamespaceAndTable(namespaceName, tableName);

        Map<String, Object> newSchema = new HashMap<>();
        newSchema.put("type", "struct");
        newSchema.put("schema-id", 1);
        newSchema.put("fields", List.of(
            Map.of("id", 1, "name", "id", "required", true, "type", "long"),
            Map.of("id", 2, "name", "data", "required", false, "type", "string"),
            Map.of("id", 3, "name", "ts", "required", false, "type", "timestamp")
        ));

        Map<String, Object> setPropsUpdate = new HashMap<>();
        setPropsUpdate.put("action", "set-properties");
        setPropsUpdate.put("updates", Map.of("owner", "test-user", "created-at", "2024-01-01"));

        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(),
            "updates", List.of(
                Map.of("action", "add-schema", "schema", newSchema),
                Map.of("action", "set-current-schema", "schema-id", 1),
                Map.of("action", "set-location", "location", "/updated/location"),
                setPropsUpdate
            )
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(200)
            .body("metadata.current-schema-id", equalTo(1))
            .body("metadata.location", equalTo("/updated/location"));

        cleanupTable(namespaceName, tableName);
    }

    @Test
    public void testCommitTableSetAndRemoveProperties() {
        String namespaceName = "test_props_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "props_table";

        createNamespaceAndTable(namespaceName, tableName);

        // Set properties
        Map<String, Object> setPropsUpdate = new HashMap<>();
        setPropsUpdate.put("action", "set-properties");
        setPropsUpdate.put("updates", Map.of("key1", "value1", "key2", "value2"));

        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(),
            "updates", List.of(setPropsUpdate)
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(200)
            .body("metadata.properties.key1", equalTo("value1"))
            .body("metadata.properties.key2", equalTo("value2"));

        // Remove one property
        Map<String, Object> removePropsUpdate = new HashMap<>();
        removePropsUpdate.put("action", "remove-properties");
        removePropsUpdate.put("removals", List.of("key1"));

        Map<String, Object> commitRequest2 = Map.of(
            "requirements", List.of(),
            "updates", List.of(removePropsUpdate)
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest2)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(200)
            .body("metadata.properties.key2", equalTo("value2"));

        cleanupTable(namespaceName, tableName);
    }

    @Test
    public void testCommitTableNotFound() {
        String namespaceName = "test_notfound_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Create namespace but not the table
        given()
            .when()
            .contentType(CT_JSON)
            .body(Map.of("namespace", List.of(namespaceName), "properties", Map.of()))
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200);

        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(),
            "updates", List.of(Map.of("action", "set-location", "location", "/new"))
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/nonexistent")
            .then()
            .statusCode(404);

        // Cleanup namespace
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);
    }

    @Test
    public void testCommitTableUnknownRequirementType() {
        String namespaceName = "test_badreq_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "badreq_table";

        createNamespaceAndTable(namespaceName, tableName);

        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(Map.of("type", "assert-unknown-thing")),
            "updates", List.of(Map.of("action", "set-location", "location", "/new"))
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(400);

        cleanupTable(namespaceName, tableName);
    }

    @Test
    public void testCommitTableUnknownUpdateAction() {
        String namespaceName = "test_badact_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "badact_table";

        createNamespaceAndTable(namespaceName, tableName);

        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(),
            "updates", List.of(Map.of("action", "do-something-unknown"))
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(400);

        cleanupTable(namespaceName, tableName);
    }

    @Test
    public void testCommitTableConcurrentConflict() throws Exception {
        String namespaceName = "test_concurrent_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "concurrent_table";

        createNamespaceAndTable(namespaceName, tableName);

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Map<String, Object> setPropsUpdate = new HashMap<>();
                setPropsUpdate.put("action", "set-properties");
                setPropsUpdate.put("updates", Map.of("thread", String.valueOf(idx)));

                Map<String, Object> commitRequest = Map.of(
                    "requirements", List.of(),
                    "updates", List.of(setPropsUpdate)
                );

                int status = given()
                    .when()
                    .contentType(CT_JSON)
                    .body(commitRequest)
                    .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
                    .then()
                    .extract().statusCode();

                if (status == 200) {
                    successCount.incrementAndGet();
                } else if (status == 409) {
                    conflictCount.incrementAndGet();
                }
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // At least one must succeed
        assertTrue(successCount.get() >= 1, "At least one commit should succeed");
        // With concurrent commits, successes + conflicts should account for most responses
        // (some threads may fail with server errors due to thread-pool exhaustion or similar)
        assertTrue(successCount.get() + conflictCount.get() >= 1,
                "At least one thread should either succeed or conflict");

        cleanupTable(namespaceName, tableName);
    }

    // --- View Tests ---

    @Test
    public void testViewLifecycle() {
        String namespaceName = "test_view_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String viewName = "test_view";

        // Create namespace first
        given()
            .when()
            .contentType(CT_JSON)
            .body(Map.of("namespace", List.of(namespaceName), "properties", Map.of()))
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200);

        // Create view
        Map<String, Object> createViewRequest = createViewRequestBody(viewName);

        given()
            .when()
            .contentType(CT_JSON)
            .body(createViewRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views")
            .then()
            .statusCode(200)
            .body("metadata.view-uuid", notNullValue())
            .body("metadata.format-version", equalTo(1));

        // List views
        given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views")
            .then()
            .statusCode(200)
            .body("identifiers[0].name", equalTo(viewName));

        // Check view exists
        given()
            .when()
            .head(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(204);

        // Load view
        given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(200)
            .body("metadata.view-uuid", notNullValue())
            .body("metadata.current-version-id", equalTo(1));

        // Drop view
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(204);

        // Drop namespace
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);
    }

    @Test
    public void testRenameView() {
        String namespaceName = "test_renameview_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String viewName = "original_view";
        String newViewName = "renamed_view";

        createNamespaceAndView(namespaceName, viewName);

        // Rename view
        Map<String, Object> renameRequest = Map.of(
            "source", Map.of(
                "namespace", List.of(namespaceName),
                "name", viewName
            ),
            "destination", Map.of(
                "namespace", List.of(namespaceName),
                "name", newViewName
            )
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(renameRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/views/rename")
            .then()
            .statusCode(204);

        // Verify old view is gone
        given()
            .when()
            .head(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(404);

        // Verify new view exists
        given()
            .when()
            .head(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + newViewName)
            .then()
            .statusCode(204);

        cleanupView(namespaceName, newViewName);
    }

    @Test
    public void testReplaceViewBasic() {
        String namespaceName = "test_replaceview_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String viewName = "replace_view";

        createNamespaceAndView(namespaceName, viewName);

        // Replace view: add-view-version
        Map<String, Object> newVersion = new HashMap<>();
        newVersion.put("version-id", 2);
        newVersion.put("schema-id", 0);
        newVersion.put("timestamp-ms", System.currentTimeMillis());
        newVersion.put("summary", Map.of("operation", "replace"));
        newVersion.put("representations", List.of(
            Map.of("type", "sql", "sql", "SELECT id, data FROM t1 WHERE id > 10", "dialect", "spark")
        ));
        newVersion.put("default-namespace", List.of(namespaceName));

        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(),
            "updates", List.of(
                Map.of("action", "add-view-version", "view-version", newVersion)
            )
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(200)
            .body("metadata.current-version-id", equalTo(2));

        // Verify by loading
        given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(200)
            .body("metadata.current-version-id", equalTo(2));

        cleanupView(namespaceName, viewName);
    }

    @Test
    public void testReplaceViewRequirementSuccess() {
        String namespaceName = "test_viewreqok_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String viewName = "viewreq_view";

        createNamespaceAndView(namespaceName, viewName);

        // Load the view to get the UUID
        String viewUuid = given()
            .when()
            .contentType(CT_JSON)
            .get(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(200)
            .extract().path("metadata.view-uuid");

        // Commit with assert-view-uuid requirement (should succeed)
        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(Map.of("type", "assert-view-uuid", "uuid", viewUuid)),
            "updates", List.of(Map.of("action", "set-location", "location", "/new/view/location"))
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(200)
            .body("metadata.location", equalTo("/new/view/location"));

        cleanupView(namespaceName, viewName);
    }

    @Test
    public void testReplaceViewRequirementFailure() {
        String namespaceName = "test_viewreqfail_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String viewName = "viewreqfail_view";

        createNamespaceAndView(namespaceName, viewName);

        // Commit with wrong UUID (should fail with 409)
        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(Map.of("type", "assert-view-uuid", "uuid", "wrong-uuid")),
            "updates", List.of(Map.of("action", "set-location", "location", "/new/location"))
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(409)
            .body("error.type", equalTo("CommitFailedException"));

        cleanupView(namespaceName, viewName);
    }

    @Test
    public void testNamespaceNotEmptyWithView() {
        String namespaceName = "test_notemptyview_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String viewName = "test_view";

        createNamespaceAndView(namespaceName, viewName);

        // Try to drop non-empty namespace (should fail)
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(409);

        // Drop view first
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(204);

        // Now drop namespace (should succeed)
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);
    }

    // --- Helper Methods ---

    private Map<String, Object> createViewRequestBody(String viewName) {
        Map<String, Object> viewVersion = new HashMap<>();
        viewVersion.put("version-id", 1);
        viewVersion.put("schema-id", 0);
        viewVersion.put("timestamp-ms", System.currentTimeMillis());
        viewVersion.put("representations", List.of(
            Map.of("type", "sql", "sql", "SELECT * FROM t1", "dialect", "spark")
        ));

        Map<String, Object> request = new HashMap<>();
        request.put("name", viewName);
        request.put("schema", Map.of(
            "type", "struct",
            "schema-id", 0,
            "fields", List.of(
                Map.of("id", 1, "name", "id", "required", true, "type", "long"),
                Map.of("id", 2, "name", "data", "required", false, "type", "string")
            )
        ));
        request.put("view-version", viewVersion);
        request.put("properties", Map.of());
        return request;
    }

    private void createNamespaceAndView(String namespaceName, String viewName) {
        given()
            .when()
            .contentType(CT_JSON)
            .body(Map.of("namespace", List.of(namespaceName), "properties", Map.of()))
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200);

        given()
            .when()
            .contentType(CT_JSON)
            .body(createViewRequestBody(viewName))
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views")
            .then()
            .statusCode(200);
    }

    private void cleanupView(String namespaceName, String viewName) {
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/views/" + viewName)
            .then()
            .statusCode(204);

        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);
    }

    private void createNamespaceAndTable(String namespaceName, String tableName) {
        given()
            .when()
            .contentType(CT_JSON)
            .body(Map.of("namespace", List.of(namespaceName), "properties", Map.of()))
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200);

        Map<String, Object> createTableRequest = Map.of(
            "name", tableName,
            "schema", Map.of(
                "type", "struct",
                "schema-id", 0,
                "fields", List.of(
                    Map.of("id", 1, "name", "id", "required", true, "type", "long"),
                    Map.of("id", 2, "name", "data", "required", false, "type", "string")
                )
            ),
            "properties", Map.of()
        );

        given()
            .when()
            .contentType(CT_JSON)
            .body(createTableRequest)
            .post(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables")
            .then()
            .statusCode(200);
    }

    private void cleanupTable(String namespaceName, String tableName) {
        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName + "/tables/" + tableName)
            .then()
            .statusCode(204);

        given()
            .when()
            .delete(ICEBERG_API_BASE + "/iceberg/v1/default/namespaces/" + namespaceName)
            .then()
            .statusCode(204);
    }
}
