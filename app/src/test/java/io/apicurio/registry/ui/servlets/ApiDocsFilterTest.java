package io.apicurio.registry.ui.servlets;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for ApiDocsFilter to verify correct URL generation and context path handling.
 */
@QuarkusTest
class ApiDocsFilterTest extends AbstractResourceTestBase {

    /**
     * Tests that spec URL generation does not create double slashes when context path is root ("/").
     * This is the fix for issue #6791.
     */
    @Test
    void testSpecUrlWithRootContextPath_NoDoubleSlash() {
        // When: fetching the Registry v3 API docs page
        String html = given()
                .when()
                .get("http://localhost:" + testPort + "/apis/registry/v3")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .extract()
                .asString();

        // Then: generated spec URL should not have double slashes
        String expectedSpecUrl = "/api-specifications/registry/v3/openapi.json";
        assertTrue(html.contains("spec-url=\"" + expectedSpecUrl + "\""),
                "Spec URL should be: " + expectedSpecUrl + ", but got: " + extractSpecUrl(html));
        assertFalse(html.contains("spec-url=\"//api-specifications"),
                "Spec URL should not start with double slashes");
    }

    /**
     * Tests spec URL generation for Registry v2 API.
     */
    @Test
    void testSpecUrlForRegistryV2() {
        // When: fetching the Registry v2 API docs page
        String html = given()
                .when()
                .get("http://localhost:" + testPort + "/apis/registry/v2")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .extract()
                .asString();

        // Then: generated spec URL should be for v2 and not have double slashes
        String expectedSpecUrl = "/api-specifications/registry/v2/openapi.json";
        assertTrue(html.contains("spec-url=\"" + expectedSpecUrl + "\""),
                "Spec URL should be: " + expectedSpecUrl + ", but got: " + extractSpecUrl(html));
        assertFalse(html.contains("//api-specifications"),
                "Spec URL should not have double slashes");
    }

    /**
     * Tests spec URL generation for Confluent Schema Registry compatibility API.
     */
    @Test
    void testSpecUrlForCcompat() {
        // When: fetching the CCompat v7 API docs page
        String html = given()
                .when()
                .get("http://localhost:" + testPort + "/apis/ccompat/v7")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .extract()
                .asString();

        // Then: generated spec URL should be for ccompat and not have double slashes
        String expectedSpecUrl = "/api-specifications/ccompat/v7/openapi.json";
        assertTrue(html.contains("spec-url=\"" + expectedSpecUrl + "\""),
                "Spec URL should be: " + expectedSpecUrl + ", but got: " + extractSpecUrl(html));
        assertFalse(html.contains("//api-specifications"),
                "Spec URL should not have double slashes");
    }

    /**
     * Tests that API title is correctly set for Registry v3.
     */
    @Test
    void testApiTitleForRegistryV3() {
        // When: fetching the Registry v3 API docs page
        String html = given()
                .when()
                .get("http://localhost:" + testPort + "/apis/registry/v3")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .extract()
                .asString();

        // Then: API title should be correct
        assertTrue(html.contains("Core Registry API (v3)"),
                "API title should be 'Core Registry API (v3)'");
    }

    /**
     * Tests that API title is correctly set for Registry v2.
     */
    @Test
    void testApiTitleForRegistryV2() {
        // When: fetching the Registry v2 API docs page
        String html = given()
                .when()
                .get("http://localhost:" + testPort + "/apis/registry/v2")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .extract()
                .asString();

        // Then: API title should be correct
        assertTrue(html.contains("Core Registry API (v2)"),
                "API title should be 'Core Registry API (v2)'");
    }

    /**
     * Tests that API title is correctly set for CCompat API.
     */
    @Test
    void testApiTitleForCcompat() {
        // When: fetching the CCompat v7 API docs page
        String html = given()
                .when()
                .get("http://localhost:" + testPort + "/apis/ccompat/v7")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .extract()
                .asString();

        // Then: API title should be correct
        assertTrue(html.contains("Confluent Schema Registry API"),
                "API title should be 'Confluent Schema Registry API'");
    }

    /**
     * Extracts the spec-url value from the HTML result for debugging.
     *
     * @param html the HTML content
     * @return the extracted spec URL or "not found"
     */
    private String extractSpecUrl(String html) {
        int start = html.indexOf("spec-url=\"");
        if (start == -1) {
            return "not found";
        }
        start += "spec-url=\"".length();
        int end = html.indexOf("\"", start);
        if (end == -1) {
            return "not found";
        }
        return html.substring(start, end);
    }
}
