package io.apicurio.registry.noprofile.ccompat.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for canonical hash fallback when canonicalHashModeEnabled is false (the default). This verifies the
 * fix for https://github.com/Apicurio/apicurio-registry/issues/4831 where the Confluent AvroConverter and
 * ProtobufConverter fail to look up schemas due to whitespace/formatting differences.
 *
 * Uses the default profile (no TestProfile), so canonicalHashModeEnabled=false.
 */
@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
public class CCompatCanonicalFallbackTest extends AbstractResourceTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Register a schema with expanded formatting, then look it up using minified formatting. Without the
     * canonical hash fallback this would fail with 40403 because the raw SHA-256 hashes differ.
     */
    @Test
    public void testLookupSchemaByContentWithCanonicalFallback() throws Exception {
        final String SUBJECT = "testCanonicalFallbackLookup";
        String expandedSchema = resourceToString("avro-expanded.avsc");

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(expandedSchema);

        // Register the expanded schema
        final Integer contentId = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(registerRequest))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        assertNotNull(contentId);

        // Look up the schema using minified content - this triggers the canonical hash fallback
        RegisterSchemaRequest lookupRequest = new RegisterSchemaRequest();
        lookupRequest.setSchema(resourceToString("avro-minified.avsc"));

        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(lookupRequest))
                .post("/ccompat/v7/subjects/{subject}", SUBJECT).then().statusCode(200);
    }

    /**
     * Register a schema with expanded formatting, then register it again with minified formatting. The
     * second registration should return the same content ID as the first, since the canonical hash matches.
     */
    @Test
    public void testRegisterSchemaWithCanonicalFallback() throws Exception {
        final String SUBJECT = "testCanonicalFallbackRegister";
        String expandedSchema = resourceToString("avro-expanded.avsc");

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(expandedSchema);

        // Register the expanded schema
        final Integer contentId1 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(registerRequest))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        assertNotNull(contentId1);

        // Register the minified schema - should return the same content ID
        RegisterSchemaRequest minifiedRequest = new RegisterSchemaRequest();
        minifiedRequest.setSchema(resourceToString("avro-minified.avsc"));

        final Integer contentId2 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(minifiedRequest))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.equalTo(contentId1)))
                .extract().body().jsonPath().get("id");

        assertEquals(contentId1, contentId2);
    }

    /**
     * Register a schema and look it up with identical content. This is a regression test ensuring the fast
     * path (raw hash exact match) still works when canonical fallback is available.
     */
    @Test
    public void testLookupSchemaByContentExactMatch() throws Exception {
        final String SUBJECT = "testCanonicalFallbackExactMatch";
        String expandedSchema = resourceToString("avro-expanded.avsc");

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(expandedSchema);

        // Register the expanded schema
        final Integer contentId = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(registerRequest))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        assertNotNull(contentId);

        // Look up using the same expanded content - should match on the fast raw hash path
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(registerRequest))
                .post("/ccompat/v7/subjects/{subject}", SUBJECT).then().statusCode(200);
    }

    // ---- Protobuf tests ----

    /**
     * Register a Protobuf schema with expanded formatting (extra blank lines, comments), then look it up
     * using minified formatting. Without the canonical hash fallback this would fail with 40403.
     */
    @Test
    public void testLookupProtobufSchemaByContentWithCanonicalFallback() throws Exception {
        final String SUBJECT = "testCanonicalFallbackProtobufLookup";
        String expandedSchema = resourceToString("protobuf-expanded.proto");

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(expandedSchema);
        registerRequest.setSchemaType("PROTOBUF");

        // Register the expanded protobuf schema
        final Integer contentId = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(registerRequest))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        assertNotNull(contentId);

        // Look up the schema using minified content - this triggers the canonical hash fallback
        RegisterSchemaRequest lookupRequest = new RegisterSchemaRequest();
        lookupRequest.setSchema(resourceToString("protobuf-minified.proto"));
        lookupRequest.setSchemaType("PROTOBUF");

        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(lookupRequest))
                .post("/ccompat/v7/subjects/{subject}", SUBJECT).then().statusCode(200);
    }

    /**
     * Register a Protobuf schema with expanded formatting, then register it again with minified formatting.
     * The second registration should return the same content ID.
     */
    @Test
    public void testRegisterProtobufSchemaWithCanonicalFallback() throws Exception {
        final String SUBJECT = "testCanonicalFallbackProtobufRegister";
        String expandedSchema = resourceToString("protobuf-expanded.proto");

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(expandedSchema);
        registerRequest.setSchemaType("PROTOBUF");

        // Register the expanded protobuf schema
        final Integer contentId1 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(registerRequest))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        assertNotNull(contentId1);

        // Register the minified schema - should return the same content ID
        RegisterSchemaRequest minifiedRequest = new RegisterSchemaRequest();
        minifiedRequest.setSchema(resourceToString("protobuf-minified.proto"));
        minifiedRequest.setSchemaType("PROTOBUF");

        final Integer contentId2 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(minifiedRequest))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.equalTo(contentId1)))
                .extract().body().jsonPath().get("id");

        assertEquals(contentId1, contentId2);
    }

    /**
     * Register a Protobuf schema and look it up with identical content. Regression test ensuring the fast
     * path (raw hash exact match) still works for Protobuf.
     */
    @Test
    public void testLookupProtobufSchemaByContentExactMatch() throws Exception {
        final String SUBJECT = "testCanonicalFallbackProtobufExactMatch";
        String expandedSchema = resourceToString("protobuf-expanded.proto");

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(expandedSchema);
        registerRequest.setSchemaType("PROTOBUF");

        // Register the expanded protobuf schema
        final Integer contentId = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(registerRequest))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        assertNotNull(contentId);

        // Look up using the same expanded content - should match on the fast raw hash path
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(registerRequest))
                .post("/ccompat/v7/subjects/{subject}", SUBJECT).then().statusCode(200);
    }
}
