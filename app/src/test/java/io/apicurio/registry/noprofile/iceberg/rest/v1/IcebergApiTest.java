package io.apicurio.registry.noprofile.iceberg.rest.v1;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Tests for the Iceberg REST Catalog API.
 */
@QuarkusTest
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
}
