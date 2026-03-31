package io.apicurio.registry.noprofile.iceberg.rest.v1;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Iceberg REST Catalog metrics.
 */
@QuarkusTest
@TestProfile(IcebergExperimentalFeaturesProfile.class)
public class IcebergMetricsTest extends AbstractResourceTestBase {

    @Test
    public void testNamespaceAndTableMetrics() {
        String ns = "metrics_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "metrics_table_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Create namespace
        given()
            .contentType(CT_JSON)
            .body(Map.of("namespace", List.of(ns), "properties", Map.of()))
            .post("/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200);

        // Create table
        Map<String, Object> schema = Map.of(
            "type", "struct",
            "schema-id", 0,
            "fields", List.of(
                Map.of("id", 1, "name", "id", "required", true, "type", "long"),
                Map.of("id", 2, "name", "data", "required", false, "type", "string")
            )
        );

        given()
            .contentType(CT_JSON)
            .body(Map.of("name", tableName, "schema", schema))
            .post("/iceberg/v1/default/namespaces/" + ns + "/tables")
            .then()
            .statusCode(200);

        // Check metrics
        String metrics = given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .extract().body().asString();

        assertTrue(metrics.contains("iceberg_namespace_operations_total"),
                "Expected iceberg_namespace_operations_total metric");
        assertTrue(metrics.contains("iceberg_table_operations_total"),
                "Expected iceberg_table_operations_total metric");

        // Cleanup
        given().delete("/iceberg/v1/default/namespaces/" + ns + "/tables/" + tableName)
            .then().statusCode(204);
        given().delete("/iceberg/v1/default/namespaces/" + ns)
            .then().statusCode(204);
    }

    @Test
    public void testCommitMetrics() {
        String ns = "commit_ns_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tableName = "commit_table_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Create namespace and table
        given()
            .contentType(CT_JSON)
            .body(Map.of("namespace", List.of(ns), "properties", Map.of()))
            .post("/iceberg/v1/default/namespaces")
            .then()
            .statusCode(200);

        Map<String, Object> schema = Map.of(
            "type", "struct",
            "schema-id", 0,
            "fields", List.of(
                Map.of("id", 1, "name", "id", "required", true, "type", "long")
            )
        );

        given()
            .contentType(CT_JSON)
            .body(Map.of("name", tableName, "schema", schema))
            .post("/iceberg/v1/default/namespaces/" + ns + "/tables")
            .then()
            .statusCode(200);

        // Commit a schema update
        Map<String, Object> newSchema = Map.of(
            "type", "struct",
            "schema-id", 1,
            "fields", List.of(
                Map.of("id", 1, "name", "id", "required", true, "type", "long"),
                Map.of("id", 2, "name", "name", "required", false, "type", "string")
            )
        );

        Map<String, Object> commitRequest = Map.of(
            "requirements", List.of(
                Map.of("type", "assert-table-uuid")
            ),
            "updates", List.of(
                Map.of("action", "add-schema", "schema", newSchema, "last-column-id", 2),
                Map.of("action", "set-current-schema", "schema-id", 1)
            )
        );

        given()
            .contentType(CT_JSON)
            .body(commitRequest)
            .post("/iceberg/v1/default/namespaces/" + ns + "/tables/" + tableName)
            .then()
            .statusCode(200);

        // Check commit metrics
        String metrics = given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .extract().body().asString();

        assertTrue(metrics.contains("iceberg_commit_duration_seconds"),
                "Expected iceberg_commit_duration_seconds metric");
        assertTrue(metrics.contains("iceberg_table_operations_total"),
                "Expected iceberg_table_operations_total metric with committed operation");

        // Cleanup
        given().delete("/iceberg/v1/default/namespaces/" + ns + "/tables/" + tableName)
            .then().statusCode(204);
        given().delete("/iceberg/v1/default/namespaces/" + ns)
            .then().statusCode(204);
    }

    @Test
    public void testErrorMetrics() {
        // Try to load a non-existent namespace to trigger an error
        given()
            .contentType(CT_JSON)
            .get("/iceberg/v1/default/namespaces/nonexistent_ns_" + System.currentTimeMillis())
            .then()
            .statusCode(404);

        // Check error metrics
        String metrics = given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .extract().body().asString();

        assertTrue(metrics.contains("iceberg_errors_total"),
                "Expected iceberg_errors_total metric");
    }
}
