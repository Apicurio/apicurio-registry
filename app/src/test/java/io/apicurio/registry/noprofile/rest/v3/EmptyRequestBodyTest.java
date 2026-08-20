package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The v3 API declares every request body as required, and the generated JAX-RS interfaces carry a
 * matching {@code @NotNull}. No Bean Validation implementation is on the classpath, so those
 * constraints are never enforced: a request with an absent or literal-null body used to reach the
 * resource method with {@code data == null} and fail with a NullPointerException, which the
 * exception mapper turned into a 500 whose body echoed the internal bean class name.
 *
 * These endpoints must answer 400 instead, and must not disclose internal types.
 */
@QuarkusTest
public class EmptyRequestBodyTest extends AbstractResourceTestBase {

    private static final String GROUP = "EmptyRequestBodyTest";
    private static final String ARTIFACT = "empty-body-artifact";

    /**
     * Endpoints that take a required request body and are reachable in the default (no-auth)
     * profile. The roleMapping endpoints are excluded because they are gated behind RBAC and answer
     * 403 before the body is ever examined.
     */
    private static String[][] endpoints() {
        String a = "/groups/" + GROUP + "/artifacts/" + ARTIFACT;
        return new String[][] { { "POST", "/admin/rules" }, { "PUT", "/admin/rules/VALIDITY" },
                { "PUT", "/admin/config/properties/apicurio.rest.deletion.artifact.enabled" },
                { "POST", "/groups" }, { "PUT", "/groups/" + GROUP },
                { "POST", "/groups/" + GROUP + "/rules" }, { "POST", "/groups/" + GROUP + "/artifacts" },
                { "PUT", a }, { "POST", a + "/versions" }, { "POST", a + "/rules" },
                { "POST", a + "/branches" }, { "PUT", a + "/versions/1" },
                { "PUT", a + "/versions/1/state" }, { "POST", a + "/versions/1/comments" },
                { "POST", "/content/references" } };
    }

    @Test
    public void testMissingRequestBodyIsRejectedWithBadRequest() throws Exception {
        createArtifact(GROUP, ARTIFACT, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);

        List<Executable> checks = new ArrayList<>();
        for (String[] endpoint : endpoints()) {
            // An absent body and a literal JSON null both deserialize to null.
            for (String payload : new String[] { "", "null" }) {
                checks.add(() -> assertRejected(endpoint[0], endpoint[1], payload));
            }
        }
        assertAll(checks);
    }

    private void assertRejected(String verb, String path, String payload) {
        ExtractableResponse<Response> response = given().when().contentType("application/json")
                .body(payload).request(verb, registryV3ApiUrl + path).then().extract();
        String body = response.body().asString();
        String where = verb + " " + path + " [body=" + (payload.isEmpty() ? "absent" : payload) + "]";

        assertEquals(400, response.statusCode(), where + " must be rejected as a bad request: " + body);
        // The API reports registry exceptions as "<SimpleName>: <message>"; assert the message part.
        String detail = response.jsonPath().getString("detail");
        assertTrue(detail != null && detail.endsWith("Request is missing a required parameter: data"),
                where + " must report the missing body, got: " + detail);
        assertFalse(body.contains("NullPointerException"), where + " must not disclose the exception type: " + body);
        assertFalse(body.contains("io.apicurio.registry"), where + " must not disclose internal class names: " + body);
    }
}
