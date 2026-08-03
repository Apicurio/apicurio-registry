package io.apicurio.registry.noprofile.rest.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.beans.CreateVersion;
import io.apicurio.registry.rest.v3.beans.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.DecoderConfig;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the HTTP compression enabled via quarkus.http.enable-compression /
 * quarkus.http.enable-decompression actually takes effect on the wire, rather than just being
 * configured. RestAssured's underlying HTTP client auto-decompresses gzip responses by default,
 * which would hide a broken configuration, so these tests disable that auto-decompression to
 * inspect the raw bytes and headers.
 */
@QuarkusTest
public class HttpCompressionTest extends AbstractResourceTestBase {

    private static final String GROUP = "HttpCompressionTest";

    private static final RestAssuredConfig NO_AUTO_DECODE = RestAssuredConfig.newConfig()
            .decoderConfig(DecoderConfig.decoderConfig().noContentDecoders());

    /**
     * Repeated content compresses well and is large enough that gzip's fixed overhead doesn't
     * dominate, so a real compression benefit shows up in the byte counts.
     */
    private static String largeJsonSchemaContent() {
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            properties.append("\"field").append(i).append("\": { \"type\": \"string\", ")
                    .append("\"description\": \"A repeated filler field used to pad this schema ")
                    .append("so compression has enough redundant content to work with.\" },");
        }
        properties.setLength(properties.length() - 1);
        return "{ \"type\": \"object\", \"properties\": { " + properties + " } }";
    }

    private static byte[] gzip(byte[] input) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(input);
        }
        return baos.toByteArray();
    }

    private static byte[] gunzip(byte[] input) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzipIn = new GZIPInputStream(new java.io.ByteArrayInputStream(input))) {
            gzipIn.transferTo(baos);
        }
        return baos.toByteArray();
    }

    @Test
    public void testResponseIsCompressedWhenClientAcceptsGzip() throws Exception {
        String artifactId = "testResponseIsCompressedWhenClientAcceptsGzip";
        String content = largeJsonSchemaContent();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        byte[] compressedBody = given().config(NO_AUTO_DECODE).header("Accept-Encoding", "gzip")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Encoding", equalTo("gzip"))
                .header("Vary", containsString("Accept-Encoding")).extract().asByteArray();

        byte[] uncompressedBody = content.getBytes(StandardCharsets.UTF_8);
        byte[] decompressedBody = gunzip(compressedBody);

        assertEquals(new String(uncompressedBody, StandardCharsets.UTF_8),
                new String(decompressedBody, StandardCharsets.UTF_8));
        assertTrue(compressedBody.length < uncompressedBody.length, "compressed response ("
                + compressedBody.length + " bytes) should be smaller than the uncompressed content ("
                + uncompressedBody.length + " bytes)");
    }

    @Test
    public void testGzippedRequestBodyIsDecompressedByServer() throws Exception {
        String artifactId = "testGzippedRequestBodyIsDecompressedByServer";
        String content = largeJsonSchemaContent();

        CreateArtifact createArtifact = CreateArtifact.builder().artifactId(artifactId)
                .artifactType(ArtifactType.JSON)
                .firstVersion(CreateVersion.builder().content(VersionContent.builder().content(content)
                        .contentType(ContentTypes.APPLICATION_JSON).build()).build())
                .build();

        byte[] requestJson = new ObjectMapper().writeValueAsBytes(createArtifact);
        byte[] gzippedRequestJson = gzip(requestJson);
        assertTrue(gzippedRequestJson.length < requestJson.length, "the request body used by this test "
                + "must actually shrink when gzipped, otherwise it isn't exercising decompression");

        given().config(NO_AUTO_DECODE).contentType(CT_JSON).header("Content-Encoding", "gzip")
                .pathParam("groupId", GROUP).body(gzippedRequestJson)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactId", equalTo(artifactId));

        given().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("properties.field0.type", equalTo("string"))
                .body("properties.field199.type", equalTo("string"));
    }

    /**
     * Verifies that a response with a content type NOT in the quarkus.http.compress-media-types
     * list is not compressed, even when the client advertises gzip support. The admin export
     * endpoint returns application/zip, which the list deliberately excludes.
     */
    @Test
    public void testResponseNotCompressedForNonListedContentType() {
        // The admin export endpoint returns application/zip, which is not in compress-media-types.
        // Even with Accept-Encoding: gzip, the response must NOT be compressed.
        byte[] rawBody = given().config(NO_AUTO_DECODE).header("Accept-Encoding", "gzip")
                .get("/registry/v3/admin/export")
                .then().statusCode(200).header("Content-Encoding", nullValue()).extract().asByteArray();

        // The response should be a valid ZIP file (starts with PK magic bytes 0x50 0x4B)
        assertTrue(rawBody.length >= 4, "export response should not be empty");
        assertEquals((byte) 0x50, rawBody[0], "response should start with ZIP magic byte 'P'");
        assertEquals((byte) 0x4B, rawBody[1], "response should start with ZIP magic byte 'K'");
    }

    /**
     * Verifies that gzip;q=0 (an explicit client refusal of gzip per RFC 9110 §12.5.3) is
     * correctly treated as a refusal, not as acceptance.
     */
    @Test
    public void testResponseNotCompressedWhenClientRefusesGzip() throws Exception {
        String artifactId = "testResponseNotCompressedWhenClientRefusesGzip";
        String content = largeJsonSchemaContent();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        byte[] rawBody = given().config(NO_AUTO_DECODE).header("Accept-Encoding", "gzip;q=0")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Encoding", nullValue()).extract().asByteArray();

        // The response should be the uncompressed content
        assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), rawBody,
                "response body with gzip;q=0 should be uncompressed");
    }

    /**
     * Baseline test: no Accept-Encoding header means no compression.
     */
    @Test
    public void testResponseNotCompressedWithoutAcceptEncodingHeader() throws Exception {
        String artifactId = "testResponseNotCompressedWithoutAcceptEncoding";
        String content = largeJsonSchemaContent();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        byte[] rawBody = given().config(NO_AUTO_DECODE)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Encoding", nullValue()).extract().asByteArray();

        assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), rawBody,
                "response body without Accept-Encoding should be uncompressed");
    }

    /**
     * Verifies that the decompression-bomb mitigation actually works: a small gzip payload that
     * decompresses to well over the 50 MB max-body-size must be rejected. This locks the
     * assumption that Vert.x applies quarkus.http.limits.max-body-size to the decompressed
     * payload, which is version-dependent behavior.
     *
     * The payload is valid JSON (a large string value) so the rejection comes from the body
     * size limit rather than a JSON parse error.
     */
    @Test
    public void testDecompressionBombRejectedByBodySizeLimit() throws Exception {
        // Build a valid JSON string that is ~60 MB uncompressed but compresses extremely well
        // because it's highly repetitive: {"d":"AAAAAA..."}
        int targetSize = 60 * 1024 * 1024;
        StringBuilder sb = new StringBuilder(targetSize + 32);
        sb.append("{\"d\":\"");
        while (sb.length() < targetSize) {
            sb.append("AAAAAAAAAA"); // 10 chars of repetitive content
        }
        sb.append("\"}");
        byte[] jsonBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] gzippedBomb = gzip(jsonBytes);

        // Sanity: the compressed payload must be well under the 50 MB limit
        assertTrue(gzippedBomb.length < 1024 * 1024,
                "the gzipped bomb (" + gzippedBomb.length + " bytes) should be small on the wire");
        assertTrue(jsonBytes.length > 50 * 1024 * 1024,
                "the uncompressed payload (" + jsonBytes.length + " bytes) must exceed the 50 MB limit");

        int statusCode = given().config(NO_AUTO_DECODE).contentType(CT_JSON)
                .header("Content-Encoding", "gzip")
                .pathParam("groupId", GROUP)
                .body(gzippedBomb)
                .post("/registry/v3/groups/{groupId}/artifacts")
                .then().extract().statusCode();

        // The server must reject this — either 413 (body size limit on decompressed bytes)
        // or another 4xx. The critical assertion is that it does NOT succeed (200/201).
        assertTrue(statusCode == 413 || statusCode == 400,
                "expected 413 (body too large) or 400 (bad request), but got " + statusCode
                        + ". If 200/201, the decompression-bomb mitigation is broken.");
    }

    /**
     * Verifies that when the runtime kill switch (apicurio.rest.compression.enabled) is active
     * (the default), responses are only single-layer gzip-compressed. This also implicitly
     * confirms the build is on Classic RESTEasy (not Reactive), since Vert.x response
     * compression for Reactive would add a second gzip layer that would break the single-gunzip
     * assertion.
     */
    @Test
    public void testSingleLayerGzipConfirmsNoDoubleCompression() throws Exception {
        String artifactId = "testSingleLayerGzipConfirmsNoDoubleCompression";
        String content = largeJsonSchemaContent();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        byte[] compressedBody = given().config(NO_AUTO_DECODE).header("Accept-Encoding", "gzip")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Encoding", equalTo("gzip")).extract().asByteArray();

        // A single gunzip must recover the original content. If the interceptor or Vert.x
        // double-compressed, this would produce garbled bytes or a still-gzipped stream.
        byte[] decompressed = gunzip(compressedBody);
        assertEquals(content, new String(decompressed, StandardCharsets.UTF_8),
                "single gunzip should recover original content — double compression would fail here");
    }
}

